package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleReceipt extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(HandleReceipt.class);

	private final DataSetStatusGenerator statusGenerator;

	public HandleReceipt(ProcessPluginApi api, DataSetStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		Task.ParameterComponent statusCodeInput = getDataSetStatusInput(latestTask);
		String statusCode = getDataSetStatusCode(statusCodeInput);
		String error = getDataSetStatusError(statusCodeInput);

		if (ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_OK.equals(statusCode))
		{
			logger.info(
					"Task with id '{}' for project-identifier '{}' and DMS with identifier '{}' has data-set status code '{}'",
					startTask.getId(), projectIdentifier, dmsIdentifier, statusCode);

			transformInputToOutput(startTask, latestTask);
			variables.updateTask(startTask);

			sendSuccessfulMail(startTask, projectIdentifier, dmsIdentifier, statusCode);
		}
		else
		{
			String errorLog = error.isBlank() ? "" : " - " + error;
			logger.warn(
					"Could not deliver encrypted transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'{}",
					dmsIdentifier, projectIdentifier, startTask.getId(), errorLog);

			String errorMessage = "Deliver encrypted transferable data-set failed" + errorLog;
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE,
					errorMessage);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, errorMessage);
		}
	}

	private Task.ParameterComponent getDataSetStatusInput(Task task)
	{
		if (task != null)
			return task.getInput().stream().filter(i -> i.getType().getCoding().stream()
					.anyMatch(c -> ConstantsDataSharing.CODESYSTEM_DATA_SHARING.equals(c.getSystem())
							&& ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS.equals(c.getCode())))
					.filter(i -> i.getValue() instanceof Coding).findFirst().orElse(getMissingReceipt());
		else
			return getMissingReceipt();
	}

	private Task.ParameterComponent getMissingReceipt()
	{
		return statusGenerator.createDataSetStatusInput(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_MISSING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS,
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_MISSING);
	}

	private String getDataSetStatusCode(Task.ParameterComponent input)
	{
		return ((Coding) input.getValue()).getCode();
	}

	private String getDataSetStatusError(Task.ParameterComponent input)
	{
		return input.hasExtension() ? input.getExtensionFirstRep().getValueAsPrimitive().getValueAsString() : "";
	}

	private void transformInputToOutput(Task startTask, Task latestTask)
	{
		if (latestTask != null)
			statusGenerator.transformInputToOutput(latestTask, startTask, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS);
	}

	private void sendSuccessfulMail(Task task, String projectIdentifier, String dmsIdentifier, String code)
	{
		String subject = "Data-set successfully delivered in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "A data-set has been successfully delivered and retrieved in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' for Task with id '" + task.getId()
				+ "' to/from DMS with identifier '" + dmsIdentifier + "' for project-identifier '" + projectIdentifier
				+ "' with status code '" + code + "'";

		api.getMailService().send(subject, message);
	}
}
