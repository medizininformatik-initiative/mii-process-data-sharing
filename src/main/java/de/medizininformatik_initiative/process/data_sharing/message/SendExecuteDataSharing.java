package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.List;

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
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SendExecuteDataSharing implements MessageSendTask
{
	public SendExecuteDataSharing()
	{
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Task.ParameterComponent dmsIdentifierInput = getDmsIdentifierInput(dmsIdentifier);

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Task.ParameterComponent projectIdentifierInput = getProjectIdentifierInput(projectIdentifier);

		String contractUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL);
		Task.ParameterComponent contractUrlInput = getContractUrlInput(contractUrl);

		return List.of(dmsIdentifierInput, projectIdentifierInput, contractUrlInput);
	}

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSenderWithTaskStorage(api, variables, sendTaskValues, getBusinessKeyStrategy(),
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target));
	}

	@Override
	public MessageSendTaskErrorHandler getErrorHandler()
	{
		return new MessageSendTaskErrorHandlerContinuingProcessWithTaskLog();
	}

	private Task.ParameterComponent getDmsIdentifierInput(String dmsIdentifier)
	{
		Task.ParameterComponent input = new Task.ParameterComponent();
		input.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER);
		input.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue(dmsIdentifier))
				.setType(ResourceType.Organization.name()));

		return input;
	}

	private Task.ParameterComponent getProjectIdentifierInput(String projectIdentifier)
	{
		Task.ParameterComponent input = new Task.ParameterComponent();
		input.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);
		input.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
				.setValue(projectIdentifier));

		return input;
	}

	private Task.ParameterComponent getContractUrlInput(String contractUrl)
	{
		Task.ParameterComponent input = new Task.ParameterComponent();
		input.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);
		input.setValue(new UrlType(contractUrl));

		return input;
	}
}
