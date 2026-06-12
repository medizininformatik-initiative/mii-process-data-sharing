package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorMergeRelease implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorMergeRelease.class);

	private final boolean dmsEmailEnabled;

	public HandleErrorMergeRelease(boolean dmsEmailEnabled)
	{
		this.dmsEmailEnabled = dmsEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String errorCode = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR);
		String errorMessage = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE);

		logger.warn("Recreating user-task 'release-merged-data-set'");
		if (dmsEmailEnabled)
			sendMail(api, startTask, projectIdentifier, errorCode, errorMessage);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE,
				null);
	}

	private void sendMail(ProcessPluginApi api, Task task, String projectIdentifier, String errorCode,
			String errorMessage)
	{
		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not merge data-sets in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' requested from organization '"
				+ task.getRequester().getIdentifier().getValue() + "' regarding project-identifier '"
				+ projectIdentifier + "':\n" + "- status code: " + errorCode + "\n" + "- error: "
				+ (errorMessage == null ? "unknown" : errorMessage) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-merged-data-set'.";

		api.getMailService().send(subject, message);
	}
}
