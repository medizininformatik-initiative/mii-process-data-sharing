package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.List;
import java.util.function.Function;

import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageSendTask;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageSendTaskErrorHandler;
import dev.dsf.bpe.v2.error.impl.ExceptionToErrorBoundaryEventTranslationErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class SendMergedDataSet implements MessageSendTask
{
	public SendMergedDataSet()
	{
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		String dataSetUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_URL);

		Task.ParameterComponent dataSetUrlInput = new Task.ParameterComponent();
		dataSetUrlInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);
		dataSetUrlInput.setValue(new UrlType().setValue(dataSetUrl));

		return List.of(dataSetUrlInput);
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

		Function<Exception, String> errorMessageTranslator = (exception) -> "Send mergedDataSet failed"
				+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();

		return new ExceptionToErrorBoundaryEventTranslationErrorHandler(errorCodeTranslator, errorMessageTranslator);
	}
}
