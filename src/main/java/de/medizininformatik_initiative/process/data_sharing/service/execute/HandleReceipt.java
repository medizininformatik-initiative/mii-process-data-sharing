package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleReceipt implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(HandleReceipt.class);

	private final DataSetStatusGenerator statusGenerator;
	private final boolean dicEmailEnabled;

	public HandleReceipt(DataSetStatusGenerator statusGenerator, boolean dicEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.dicEmailEnabled = dicEmailEnabled;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		String resourceVersion = api.getProcessPluginDefinition().getResourceVersion();

		Task.ParameterComponent statusCodeInput = getDataSetStatusInput(latestTask, resourceVersion);
		String statusCode = getDataSetStatusCode(statusCodeInput);
		String error = getDataSetStatusError(statusCodeInput);

		if (ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_OK.equals(statusCode))
		{
			logger.info(
					"Delivering encrypted data-set for DMS '{}' and project-identifier '{}' has status code '{}' in Task '{}'",
					dmsIdentifier, projectIdentifier, statusCode,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(startTask));

			transformInputToOutput(startTask, latestTask, resourceVersion);
			variables.updateTask(startTask);

			if (dicEmailEnabled)
				sendSuccessfulMail(api, startTask, projectIdentifier, dmsIdentifier, statusCode);
		}
		else
		{
			String errorLog = error.isBlank() ? "" : ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + error;
			logger.warn("Could not deliver encrypted data-set for DMS '{}' and project-identifier '{}' in Task '{}'{}",
					dmsIdentifier, projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(startTask),
					errorLog);

			throw new ErrorBoundaryEvent(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_SENT,
					"Deliver encrypted data-set failed" + errorLog);
		}
	}

	private Task.ParameterComponent getDataSetStatusInput(Task task, String resourceVersion)
	{
		if (task != null)
			return task.getInput().stream().filter(i -> i.getType().getCoding().stream()
					.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
							&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS.equals(c.getCode())))
					.filter(i -> i.getValue() instanceof Coding).findFirst().orElse(getMissingReceipt(resourceVersion));
		else
			return getMissingReceipt(resourceVersion);
	}

	private Task.ParameterComponent getMissingReceipt(String resourceVersion)
	{
		return statusGenerator.createDataSetStatusInput(resourceVersion,
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_MISSING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING, resourceVersion,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS);
	}

	private String getDataSetStatusCode(Task.ParameterComponent input)
	{
		return ((Coding) input.getValue()).getCode();
	}

	private String getDataSetStatusError(Task.ParameterComponent input)
	{
		return input.hasExtension() ? input.getExtensionFirstRep().getValueAsPrimitive().getValueAsString() : "";
	}

	private void transformInputToOutput(Task startTask, Task latestTask, String resourceVersion)
	{
		if (latestTask != null)
			statusGenerator.transformInputToOutput(latestTask, startTask, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					resourceVersion, ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS);
	}

	private void sendSuccessfulMail(ProcessPluginApi api, Task task, String projectIdentifier, String dmsIdentifier,
			String code)
	{
		String subject = "Data-set successfully delivered in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "A data-set has been successfully delivered and retrieved in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' to/from DMS '" + dmsIdentifier
				+ "' regarding project-identifier '" + projectIdentifier + "' with status code '" + code + "'";

		api.getMailService().send(subject, message);
	}
}
