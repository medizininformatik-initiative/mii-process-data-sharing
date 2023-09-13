package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleErrorMergeRelease extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorMergeRelease.class);

	public HandleErrorMergeRelease(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE);

		sendMail(startTask, projectIdentifier, error);
	}

	private void sendMail(Task startTask, String projectIdentifier, String error)
	{
		logger.warn("{} - creating new user-task 'release-merged-data-set'", error);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not merge data-sets in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + startTask.getId()
				+ "' requested from organization '" + startTask.getRequester().getIdentifier().getValue()
				+ "' for project-identifier '" + projectIdentifier + "':\n" + "- error: "
				+ (error == null ? "none" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-merged-data-set'.";

		api.getMailService().send(subject, message);
	}
}
