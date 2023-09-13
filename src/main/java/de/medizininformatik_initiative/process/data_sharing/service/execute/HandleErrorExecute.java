package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Optional;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleErrorExecute extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorExecute.class);

	public HandleErrorExecute(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE);

		Optional<String> statusCode = extractStatusCode(latestTask);

		sendMail(startTask, statusCode.orElse("unknown"), dmsIdentifier, projectIdentifier, error);
		failTaskIfNotStartTask(latestTask, statusCode.isPresent());
	}

	private void sendMail(Task startTask, String code, String dmsIdentifier, String projectIdentifier, String error)
	{
		logger.warn("{} - creating new user-startTask 'release-data-set'", error);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "Could not send data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' for Task with id '"
				+ startTask.getId() + "' to DMS with identifier '" + dmsIdentifier + "' for project-identifier '"
				+ projectIdentifier + "':\n" + "- status code: " + code + "\n" + "- error: "
				+ (error == null ? "none" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-data-set'.";

		api.getMailService().send(subject, message);
	}

	private Optional<String> extractStatusCode(Task task)
	{
		if (task != null)
		{
			return task.getOutput().stream().filter(o -> o.getValue() instanceof Coding).map(o -> (Coding) o.getValue())
					.filter(c -> ConstantsBase.CODESYSTEM_DATA_SET_STATUS.equals(c.getSystem())).map(c -> c.getCode())
					.findFirst();
		}
		else
			return Optional.empty();
	}

	private void failTaskIfNotStartTask(Task latestTask, Boolean codeExists)
	{
		if (latestTask != null && codeExists)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.update(latestTask);
		}
	}
}
