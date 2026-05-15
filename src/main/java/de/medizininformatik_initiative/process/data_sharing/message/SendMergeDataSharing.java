package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageSendTask;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.error.impl.DefaultMessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class SendMergeDataSharing implements MessageSendTask
{
	public SendMergeDataSharing()
	{
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Task.ParameterComponent projectIdentifierInput = getProjectIdentifierInput(api, projectIdentifier);

		String contractUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL);
		Task.ParameterComponent contractUrlInput = getContractUrlInput(api, contractUrl);

		List<Task.ParameterComponent> otherInputs = List.of(projectIdentifierInput, contractUrlInput);

		List<String> researcherIdentifiers = (variables
				.getStringList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS));
		List<Task.ParameterComponent> researcherIdentifierInputs = getResearcherIdentifierInputs(api,
				researcherIdentifiers);

		Targets targets = variables.getTargets();
		List<Task.ParameterComponent> correlationKeyInputs = getCorrelationKeyInputs(api, targets);

		return Stream.of(otherInputs, researcherIdentifierInputs, correlationKeyInputs).flatMap(Collection::stream)
				.toList();
	}

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSender(api, variables, sendTaskValues, getBusinessKeyStrategy(),
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target));
	}

	@Override
	public MessageSendTaskErrorHandler getErrorHandler()
	{
		return new DefaultMessageSendTaskErrorHandler();
	}

	private Task.ParameterComponent getProjectIdentifierInput(ProcessPluginApi api, String projectIdentifier)
	{
		Task.ParameterComponent projectIdentifierInput = new Task.ParameterComponent();
		projectIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);
		projectIdentifierInput.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
				.setValue(projectIdentifier));

		return projectIdentifierInput;
	}

	private Task.ParameterComponent getContractUrlInput(ProcessPluginApi api, String contractUrl)
	{
		Task.ParameterComponent contractUrlInput = new Task.ParameterComponent();
		contractUrlInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);
		contractUrlInput.setValue(new UrlType(contractUrl));

		return contractUrlInput;
	}

	private List<Task.ParameterComponent> getResearcherIdentifierInputs(ProcessPluginApi api, List<String> researchers)
	{
		return researchers.stream().map(r -> transformToResearcherInput(api, r)).toList();
	}

	private Task.ParameterComponent transformToResearcherInput(ProcessPluginApi api, String researcherIdentifier)
	{
		Task.ParameterComponent input = new Task.ParameterComponent();
		input.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);
		input.setValue(new Identifier().setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER)
				.setValue(researcherIdentifier));

		return input;
	}

	private List<Task.ParameterComponent> getCorrelationKeyInputs(ProcessPluginApi api, Targets targets)
	{
		return targets.getEntries().stream().map(t -> transformToTargetInput(api, t)).toList();
	}

	private Task.ParameterComponent transformToTargetInput(ProcessPluginApi api, Target target)
	{
		Task.ParameterComponent input = new Task.ParameterComponent();
		input.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY);
		input.setValue(new StringType(target.getCorrelationKey()));

		input.addExtension().setUrl(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER)
				.setValue(new Reference()
						.setIdentifier(
								NamingSystems.OrganizationIdentifier.withValue(target.getOrganizationIdentifierValue()))
						.setType(ResourceType.Organization.name()));

		return input;
	}
}
