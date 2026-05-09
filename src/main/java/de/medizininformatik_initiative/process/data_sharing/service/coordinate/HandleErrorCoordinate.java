package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorCoordinate implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorCoordinate.class);

	private final boolean hrpEmailEnabled;

	public HandleErrorCoordinate(boolean hrpEmailEnabled)
	{
		this.hrpEmailEnabled = hrpEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.warn("Recreating user-task 'release-consolidate-data-sets'");

		if (hrpEmailEnabled)
			sendMail(api, variables);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR_MESSAGE, null);
	}

	private void sendMail(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR_MESSAGE);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "'";
		String message = "Could not start consolidate data-sets in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' at DMS '" + dmsIdentifier
				+ "' regarding project-identifier '" + projectIdentifier + "':\n" + "- status code: "
				+ ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR + "\n" + "- error: "
				+ (error == null ? "unknown" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-consolidate-data-sets'.";

		api.getMailService().send(subject, message);
	}
}
