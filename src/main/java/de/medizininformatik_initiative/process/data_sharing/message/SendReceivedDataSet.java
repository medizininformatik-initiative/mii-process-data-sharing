package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.error.MessageEndEventErrorHandlerWithTaskOutput;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageEndEvent;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageEndEventErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class SendReceivedDataSet implements MessageEndEvent, InitializingBean
{
	private final ProcessPluginApi api;
	private final DataSetStatusGenerator statusGenerator;

	public SendReceivedDataSet(ProcessPluginApi api, DataSetStatusGenerator statusGenerator)
	{
		this.api = api;
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		Task task = variables.getLatestTask();
		Reference requester = task.getRequester();

		Task.ParameterComponent input = api.getTaskHelper().createInput(requester,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER,
				api.getProcessPluginDefinition().getResourceVersion());

		return List.of(input);
	}

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSender(api, variables, sendTaskValues, getBusinessKeyStrategy(),
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target));
	}

	@Override
	public MessageEndEventErrorHandler getErrorHandler()
	{
		return new MessageEndEventErrorHandlerWithTaskOutput(getOutputGenerator());
	}

	private Function<Exception, Task.TaskOutputComponent> getOutputGenerator()
	{
		return (exception) ->
		{
			String statusCode = ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_REACHABLE;
			if (exception instanceof WebApplicationException webApplicationException
					&& webApplicationException.getResponse() != null
					&& webApplicationException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
			{
				statusCode = ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_ALLOWED;
			}

			return statusGenerator.createDataSetStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
					statusCode, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					api.getProcessPluginDefinition().getResourceVersion(),
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS,
					"Send received data-set failed");
		};
	}
}
