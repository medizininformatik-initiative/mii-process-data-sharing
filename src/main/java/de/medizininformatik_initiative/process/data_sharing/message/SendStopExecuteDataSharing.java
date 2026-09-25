package de.medizininformatik_initiative.process.data_sharing.message;

import de.medizininformatik_initiative.processes.common.activity.RetryTaskSenderWithTaskStorage;
import de.medizininformatik_initiative.processes.common.error.MessageSendTaskErrorHandlerContinuingProcessWithTaskLog;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageSendTask;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.variables.Variables;

public class SendStopExecuteDataSharing implements MessageSendTask
{
	public SendStopExecuteDataSharing()
	{
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
}
