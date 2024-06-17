package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleErrorMergeReceiveDownloadInsert extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorMergeReceiveDownloadInsert.class);

	public HandleErrorMergeReceiveDownloadInsert(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE);

		sendMail(startTask, projectIdentifier, error);
		failTaskIfNotStartTask(startTask, latestTask, variables);
	}

	private void sendMail(Task task, String projectIdentifier, String error)
	{
		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not download and insert new data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + task.getId()
				+ "' from organization '" + task.getRequester().getIdentifier().getValue()
				+ "' for project-identifier '" + projectIdentifier + "'.\n\nError:"
				+ (error == null ? "Unknown" : error);

		api.getMailService().send(subject, message);
	}

	private void failTaskIfNotStartTask(Task startTask, Task latestTask, Variables variables)
	{
		if (latestTask != null && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.update(latestTask);
			variables.updateTask(latestTask);
		}
	}
}
