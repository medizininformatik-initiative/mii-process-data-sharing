package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSenderWithTaskStorage;
import de.medizininformatik_initiative.processes.common.error.MessageSendTaskErrorHandlerContinuingProcessWithTaskLog;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageSendTask;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class SendInitializeNewProjectDataSharing implements MessageSendTask
{
	private final String fhirStoreId;

	public SendInitializeNewProjectDataSharing(String fhirStoreId)
	{
		this.fhirStoreId = fhirStoreId;
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Task.ParameterComponent projectIdentifierInput = getProjectIdentifierInput(projectIdentifier);

		String contractUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL);
		Task.ParameterComponent contractUrlInput = getContractUrlInput(contractUrl);

		List<Task.ParameterComponent> otherInputs = List.of(projectIdentifierInput, contractUrlInput);

		List<String> researcherIdentifiers = variables
				.getStringList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS);
		List<Task.ParameterComponent> researcherIdentifierInputs = getResearcherIdentifierInputs(researcherIdentifiers);

		Targets targets = variables.getTargets();
		List<Task.ParameterComponent> dicIdentifierInputs = getDicIdentifierInputs(targets);

		return Stream.of(otherInputs, researcherIdentifierInputs, dicIdentifierInputs).flatMap(Collection::stream)
				.toList();
	}

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSenderWithTaskStorage(api, variables, sendTaskValues, getBusinessKeyStrategy(),
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target))
		{
			@Override
			protected IdType doSend(Task task, String targetEndpointUrl)
			{
				return api.getDsfClientProvider().getById(fhirStoreId)
						.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"))
						.withMinimalReturn().withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
								DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN))
						.create(task);
			}
		};
	}

	@Override
	public MessageSendTaskErrorHandler getErrorHandler()
	{
		return new MessageSendTaskErrorHandlerContinuingProcessWithTaskLog();
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

	private List<Task.ParameterComponent> getResearcherIdentifierInputs(List<String> researchers)
	{
		return researchers.stream().map(this::transformToResearcherInput).toList();
	}

	private Task.ParameterComponent transformToResearcherInput(String researcherIdentifier)
	{
		Task.ParameterComponent researcherIdentifierInput = new Task.ParameterComponent();
		researcherIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);
		researcherIdentifierInput.setValue(new Identifier()
				.setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER).setValue(researcherIdentifier));

		return researcherIdentifierInput;
	}

	private List<Task.ParameterComponent> getDicIdentifierInputs(Targets targets)
	{
		return targets.getEntries().stream().map(this::transformToInput).toList();
	}

	private Task.ParameterComponent transformToInput(Target target)
	{
		Task.ParameterComponent dicIdentifierInput = new Task.ParameterComponent();
		dicIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);
		dicIdentifierInput.setValue(new Reference()
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(target.getOrganizationIdentifierValue()))
				.setType(ResourceType.Organization.name()));

		return dicIdentifierInput;
	}
}
