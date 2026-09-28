package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleErrorMergeReceiveDownloadInsert extends AbstractServiceDelegate
{
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
				+ "' for project-identifier '" + projectIdentifier + "'.\n\nError:\n"
				+ (error == null ? "Unknown" : error);

		api.getMailService().send(subject, message);
	}

	private void failTaskIfNotStartTask(Task startTask, Task latestTask, Variables variables)
	{
		if (latestTask != null && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			variables.updateTask(latestTask);
		}
	}
}
