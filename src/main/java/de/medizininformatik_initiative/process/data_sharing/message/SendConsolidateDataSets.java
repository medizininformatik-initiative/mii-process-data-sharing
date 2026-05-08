package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.function.Function;

import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageSendTask;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.error.impl.ExceptionToErrorBoundaryEventTranslationErrorHandler;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class SendConsolidateDataSets implements MessageSendTask
{
	public SendConsolidateDataSets()
	{
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
		Function<Exception, String> errorCodeTranslator = (exception) ->
		{
			String errorCode = ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_REACHABLE;
			if (exception instanceof WebApplicationException webApplicationException
					&& webApplicationException.getResponse() != null
					&& webApplicationException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
			{
				errorCode = ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_ALLOWED;
			}

			return errorCode;
		};

		Function<Exception, String> errorMessageTranslator = (exception) -> "Send consolidateDataSets failed"
				+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();

		return new ExceptionToErrorBoundaryEventTranslationErrorHandler(errorCodeTranslator, errorMessageTranslator);
	}
}
