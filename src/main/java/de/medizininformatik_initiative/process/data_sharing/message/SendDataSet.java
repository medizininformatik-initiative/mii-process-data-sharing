package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.List;
import java.util.function.Function;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task.ParameterComponent;

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

public class SendDataSet implements MessageSendTask
{
	public SendDataSet()
	{
	}

	@Override
	public List<ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		String version = api.getProcessPluginDefinition().getResourceVersion();
		String documentReferenceId = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DOCUMENT_REFERENCE_LOCATION);

		ParameterComponent documentReferenceComponent = new ParameterComponent();
		documentReferenceComponent.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(version)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_LOCATION);
		documentReferenceComponent.setValue(
				new Reference().setType(ResourceType.DocumentReference.name()).setReference(documentReferenceId));


		return List.of(documentReferenceComponent);
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

		Function<Exception, String> errorMessageTranslator = (exception) -> "Send dataSet failed"
				+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();

		return new ExceptionToErrorBoundaryEventTranslationErrorHandler(errorCodeTranslator, errorMessageTranslator);
	}
}
