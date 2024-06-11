package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class SendExecuteDataSharing extends AbstractTaskMessageSend
{
	private static final Logger logger = LoggerFactory.getLogger(SendExecuteDataSharing.class);

	public SendExecuteDataSharing(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Task.ParameterComponent dmsIdentifierInput = getDmsIdentifierInput(dmsIdentifier);

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Task.ParameterComponent projectIdentifierInput = getProjectIdentifierInput(projectIdentifier);

		String contractUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL);
		Task.ParameterComponent contractUrlInput = getContractUrlInput(contractUrl);

		return Stream.of(dmsIdentifierInput, projectIdentifierInput, contractUrlInput);
	}

	@Override
	protected IdType doSend(FhirWebserviceClient client, Task task)
	{
		try
		{
			return client.withMinimalReturn()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.create(task);
		}
		catch (Exception exception)
		{
			String taskJson = api.getFhirContext().newJsonParser().encodeResourceToString(task);
			String recipient = task.getRestriction().getRecipient().stream().filter(Reference::hasIdentifier)
					.map(r -> r.getIdentifier().getValue()).findFirst().orElse("unknown");
			String projectIdentifier = task.getInput().stream()
					.filter(i -> i.getType().getCoding().stream()
							.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
									&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER
											.equals(c.getCode())))
					.filter(Task.ParameterComponent::hasValue).map(Task.ParameterComponent::getValue)
					.filter(t -> t instanceof Identifier).map(t -> (Identifier) t).map(Identifier::getValue).findFirst()
					.orElse("unknown");

			logger.warn(
					"Could not start data extraction process at DIC with identifier '{}' for project-identifier '{}' "
							+ "- task json for later attempt: {}",
					recipient, projectIdentifier, taskJson);

			throw exception;
		}
	}

	private Task.ParameterComponent getDmsIdentifierInput(String dmsIdentifier)
	{
		return api.getTaskHelper().createInput(
				new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue(dmsIdentifier))
						.setType(ResourceType.Organization.name()),
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER);
	}

	private Task.ParameterComponent getProjectIdentifierInput(String projectIdentifier)
	{
		Task.ParameterComponent projectIdentifierInput = new Task.ParameterComponent();
		projectIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);
		projectIdentifierInput.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
				.setValue(projectIdentifier));

		return projectIdentifierInput;
	}

	private Task.ParameterComponent getContractUrlInput(String contractUrl)
	{
		Task.ParameterComponent contractUrlInput = new Task.ParameterComponent();
		contractUrlInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);
		contractUrlInput.setValue(new UrlType(contractUrl));

		return contractUrlInput;
	}
}
