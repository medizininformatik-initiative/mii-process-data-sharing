package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.List;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.MimeTypeHelper;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class ValidateDataSetMerge extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValidateDataSetMerge.class);

	private final MimeTypeHelper mimeTypeHelper;
	private final DataSetStatusGenerator statusGenerator;

	public ValidateDataSetMerge(ProcessPluginApi api, MimeTypeHelper mimeTypeHelper,
			DataSetStatusGenerator statusGenerator)
	{
		super(api);

		this.mimeTypeHelper = mimeTypeHelper;
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(mimeTypeHelper, "mimeTypeHelper");
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getLatestTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Bundle bundle = variables.getResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET);

		logger.info(
				"Validating decrypted data-set from organization '{}' for data-sharing project '{}' in Task with id '{}'",
				sendingOrganization, projectIdentifier, task.getId());

		try
		{
			validate(bundle, variables);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createDataSetStatusOutput(
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "Validate data-set failed"));
			variables.updateTask(task);

			logger.warn(
					"Could not validate data-set with id '{}' from organization '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE),
					sendingOrganization, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Validate data-set failed - " + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
					error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, error,
					exception);
		}
	}

	private void validate(Bundle bundle, Variables variables)
	{
		Bundle.BundleType type = bundle.getType();
		if (!Bundle.BundleType.TRANSACTION.equals(type))
		{
			throw new RuntimeException("Bundle is not of type Transaction (" + type + ")");
		}

		List<Bundle.BundleEntryComponent> entries = bundle.getEntry();

		int countE = entries.size();
		if (countE != 2)
		{
			throw new RuntimeException("Bundle contains " + countE + " entries (expected 2)");
		}

		List<DocumentReference> documentReferences = entries.stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(r -> r instanceof DocumentReference).map(r -> (DocumentReference) r).toList();

		long countDr = documentReferences.size();
		if (countDr != 1)
		{
			throw new RuntimeException("Bundle contains " + countDr + " DocumentReferences (expected 1)");
		}

		String identifierRequester = variables.getLatestTask().getRequester().getIdentifier().getValue();
		String identifierAuthor = documentReferences.stream().filter(DocumentReference::hasAuthor)
				.flatMap(dr -> dr.getAuthor().stream()).filter(Reference::hasIdentifier).map(Reference::getIdentifier)
				.filter(Identifier::hasValue).map(Identifier::getValue).findFirst().orElse("no-author");
		if (!identifierAuthor.equals(identifierRequester))
		{
			throw new RuntimeException("Requester in Task does not match author in DocumentReference ("
					+ identifierRequester + " != " + identifierAuthor + ")");
		}

		List<String> projectIdentifiersDocumentReference = documentReferences.stream()
				.filter(DocumentReference::hasMasterIdentifier).map(DocumentReference::getMasterIdentifier)
				.filter(mi -> ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER.equals(mi.getSystem()))
				.map(Identifier::getValue).filter(Objects::nonNull).toList();
		long countMi = projectIdentifiersDocumentReference.size();
		if (countMi != 1)
		{
			throw new RuntimeException("DocumentReference contains " + countMi + " projectIdentifiers (expected 1)");
		}

		String projectIdentifierTask = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		if (!projectIdentifiersDocumentReference.get(0).equals(projectIdentifierTask))
		{
			throw new RuntimeException("DocumentReference and Task projectIdentifier do not match  ("
					+ projectIdentifiersDocumentReference.get(0) + " != " + projectIdentifierTask + ")");
		}

		List<Resource> resources = entries.stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(r -> r != documentReferences.get(0)).toList();

		long countR = resources.size();
		if (countR != 1)
		{
			throw new RuntimeException("Bundle contains " + countR + " Resources (expected 1)");
		}

		Resource resource = resources.get(0);
		String mimeTypeR = mimeTypeHelper.getMimeType(resource);
		byte[] dataR = mimeTypeHelper.getData(resource);
		mimeTypeHelper.validate(dataR, mimeTypeR);
	}
}
