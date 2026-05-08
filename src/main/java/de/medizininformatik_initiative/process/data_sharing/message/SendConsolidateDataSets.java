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

	// TODO: send eMail in own Activity
	// @Override
	// protected void handleSendTaskError(DelegateExecution execution, Variables variables, Exception exception,
	// String errorMessage)
	// {
	// String startTaskId = variables.getStartTask().getId();
	// String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
	// String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
	// String error = "Start consolidate data-sets - " + exception.getMessage();
	//
	// logger.warn(
	// "Could not start consolidate data-sets for project-identifier '{}' at DMS with identifier '{}' referenced in Task
	// with id '{}' - {}: creating new user-task 'release-consolidate-data-sets'",
	// projectIdentifier, dmsIdentifier, startTaskId, exception.getMessage());
	//
	// sendMail(startTaskId, projectIdentifier, dmsIdentifier, error);
	//
	// throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR, error);
	// }
	//
	// private void sendMail(String startTaskId, String dmsIdentifier, String projectIdentifier, String error)
	// {
	// String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "'";
	// String message = "Could not start consolidate data-sets in process '"
	// + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "' for Task with id '" + startTaskId
	// + "' at DMS with identifier '" + dmsIdentifier + "' for project-identifier '" + projectIdentifier
	// + "'.\n\nError:\n" + (error == null ? "none" : error) + "\n\n"
	// + "Please repair the error and answer again the new user-task 'release-consolidate-data-sets'.";
	//
	// api.getMailService().send(subject, message);
	// }
}
