package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CreateDataSetBundle extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(CreateDataSetBundle.class);
	private final DataLogger dataLogger;

	public CreateDataSetBundle(ProcessPluginApi api, DataLogger dataLogger)
	{
		super(api);
		this.dataLogger = dataLogger;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(dataLogger, "dataLogger");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		logger.info(
				"Creating transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				dmsIdentifier, projectIdentifier, task.getId());

		try
		{
			DocumentReference documentReference = (DocumentReference) execution
					.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DOCUMENT_REFERENCE);
			Resource resource = (Resource) execution
					.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_RESOURCE);

			Bundle bundle = createTransactionBundle(projectIdentifier, documentReference, resource);
			dataLogger.logResource("Created data-set Bundle", bundle);

			variables.setResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET, bundle);
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not create transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					dmsIdentifier, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Create transferable data-set failed - " + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE, error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, error,
					exception);
		}
	}

	private Bundle createTransactionBundle(String projectIdentifier, DocumentReference documentReference,
			Resource resource)
	{
		Resource attachmentToTransmit = resource.setId(UUID.randomUUID().toString());

		DocumentReference documentReferenceToTransmit = new DocumentReference()
				.setStatus(Enumerations.DocumentReferenceStatus.CURRENT)
				.setDocStatus(DocumentReference.ReferredDocumentStatus.FINAL);
		documentReferenceToTransmit.setId(UUID.randomUUID().toString());
		documentReferenceToTransmit.getMasterIdentifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
				.setValue(projectIdentifier);
		documentReferenceToTransmit.addAuthor().setType(ResourceType.Organization.name())
				.setIdentifier(api.getOrganizationProvider().getLocalOrganizationIdentifier()
						.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifier is empty")));
		documentReferenceToTransmit.setDate(documentReference.getDate());

		String contentType = getFirstAttachmentContentType(documentReference, projectIdentifier);
		documentReferenceToTransmit.addContent().getAttachment().setContentType(contentType)
				.setUrl("urn:uuid:" + resource.getId());

		Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
		bundle.addEntry().setResource(documentReferenceToTransmit)
				.setFullUrl("urn:uuid:" + documentReferenceToTransmit.getId()).getRequest()
				.setMethod(Bundle.HTTPVerb.POST).setUrl(ResourceType.DocumentReference.name());
		bundle.addEntry().setResource(attachmentToTransmit).setFullUrl("urn:uuid:" + attachmentToTransmit.getId())
				.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(attachmentToTransmit.getResourceType().name());

		return bundle;
	}

	private String getFirstAttachmentContentType(DocumentReference documentReference, String projectIdentifier)
	{
		List<Attachment> attachments = Stream.of(documentReference).filter(DocumentReference::hasContent)
				.flatMap(dr -> dr.getContent().stream())
				.filter(DocumentReference.DocumentReferenceContentComponent::hasAttachment)
				.map(DocumentReference.DocumentReferenceContentComponent::getAttachment).filter(Attachment::hasUrl)
				.toList();

		if (attachments.size() < 1)
			throw new IllegalArgumentException(
					"Could not find any attachment in DocumentReference with masterIdentifier '" + projectIdentifier
							+ "' stored on KDS FHIR server");

		return attachments.get(0).getContentType();
	}
}
