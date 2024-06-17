package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClient;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
import dev.dsf.bpe.v1.variables.Variables;

public class InsertDataSet extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(InsertDataSet.class);
	private final FhirClientFactory fhirClientFactory;
	private final DataSetStatusGenerator statusGenerator;

	public InsertDataSet(ProcessPluginApi api, FhirClientFactory fhirClientFactory,
			DataSetStatusGenerator statusGenerator)
	{
		super(api);

		this.fhirClientFactory = fhirClientFactory;
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task latestTask = variables.getLatestTask();
		String sendingOrganization = latestTask.getRequester().getIdentifier().getValue();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Bundle bundle = variables.getResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET);

		FhirClient fhirClient = fhirClientFactory.getFhirClient();

		logger.info(
				"Inserting decrypted data-set in FHIR server with baseUrl '{}' from organization '{}' for data-sharing project '{}' in Task with id '{}'",
				fhirClient.getFhirBaseUrl(), sendingOrganization, projectIdentifier, latestTask.getId());

		try
		{
			List<IdType> idsOfCreatedResources = storeData(fhirClient, bundle, sendingOrganization, projectIdentifier,
					variables);

			latestTask.addOutput(
					statusGenerator.createDataSetStatusOutput(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_OK,
							ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
							ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS));
			variables.updateTask(latestTask);

			sendMail(latestTask, idsOfCreatedResources, sendingOrganization, projectIdentifier);
		}
		catch (Exception exception)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			latestTask.addOutput(statusGenerator.createDataSetStatusOutput(
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "Insert data-set failed"));
			variables.updateTask(latestTask);

			logger.warn(
					"Could not insert data-set with id '{}' from organization '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE),
					sendingOrganization, projectIdentifier, latestTask.getId(), exception.getMessage());

			String error = "Insert data-set failed - " + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
					error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, error,
					exception);
		}
	}

	private List<IdType> storeData(FhirClient fhirClient, Bundle bundle, String organizationIdentifier,
			String projectIdentifier, Variables variables)
	{
		Bundle stored = fhirClient.executeTransaction(bundle);

		List<IdType> idsOfCreatedResources = stored.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResponse)
				.map(Bundle.BundleEntryComponent::getResponse).map(Bundle.BundleEntryResponseComponent::getLocation)
				.map(IdType::new).map(this::setIdBase).toList();

		idsOfCreatedResources.stream().filter(i -> ResourceType.DocumentReference.name().equals(i.getResourceType()))
				.forEach(i -> addOutputToStartTask(i, variables));

		idsOfCreatedResources.forEach(id -> toLogMessage(id, organizationIdentifier, projectIdentifier));

		Targets targets = variables.getTargets();
		removeOrganizationFromTargets(targets, organizationIdentifier, variables);

		return idsOfCreatedResources;
	}

	private void sendMail(Task task, List<IdType> idsOfCreatedResources, String sendingOrganization,
			String projectIdentifier)
	{
		String subject = "New data-set received in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		StringBuilder message = new StringBuilder("A new data-set has been stored in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + task.getId()
				+ "' received from organization '" + sendingOrganization + "' for project-identifier '"
				+ projectIdentifier + "' with status code '" + ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_OK
				+ "' and can be accessed using the following links:\n");

		for (IdType id : idsOfCreatedResources)
			message.append("- ").append(id.getValue()).append("\n");

		api.getMailService().send(subject, message.toString());
	}

	private IdType setIdBase(IdType idType)
	{
		String fhirBaseUrl = fhirClientFactory.getFhirClient().getFhirBaseUrl();
		return new IdType(fhirBaseUrl, idType.getResourceType(), idType.getIdPart(), idType.getVersionIdPart());
	}

	private void addOutputToStartTask(IdType id, Variables variables)
	{
		Task task = variables.getStartTask();

		task.addOutput().setValue(new Reference(id.getValue()).setType(id.getResourceType())).getType().addCoding()
				.setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_REFERENCE);

		variables.updateTask(task);
	}

	private void toLogMessage(IdType idType, String sendingOrganization, String projectIdentifier)
	{
		logger.info(
				"Stored {} with id '{}' on FHIR server with baseUrl '{}' received from organization '{}' for  data-sharing project '{}'",
				idType.getResourceType(), idType.getIdPart(), idType.getBaseUrl(), sendingOrganization,
				projectIdentifier);
	}

	private void removeOrganizationFromTargets(Targets targets, String organizationIdentifier, Variables variables)
	{
		List<Target> targetsWithoutReceivedIdentifier = targets.getEntries().stream()
				.filter(t -> !organizationIdentifier.equals(t.getOrganizationIdentifierValue()))
				.collect(Collectors.toList());
		variables.setTargets(variables.createTargets(targetsWithoutReceivedIdentifier));
	}
}
