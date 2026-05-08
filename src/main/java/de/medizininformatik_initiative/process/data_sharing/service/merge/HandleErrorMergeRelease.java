package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.service.MailService;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorMergeRelease implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorMergeRelease.class);

	public HandleErrorMergeRelease()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE);

		sendMail(api.getMailService(), startTask, projectIdentifier, error);
	}

	private void sendMail(MailService mailService, Task startTask, String projectIdentifier, String error)
	{
		logger.warn("{} - creating new user-task 'release-merged-data-set'", error);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not merge data-sets in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + startTask.getId()
				+ "' requested from organization '" + startTask.getRequester().getIdentifier().getValue()
				+ "' for project-identifier '" + projectIdentifier + "'.\n\nError:\n"
				+ (error == null ? "Unknown" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-merged-data-set'.";

		mailService.send(subject, message);
	}
}
