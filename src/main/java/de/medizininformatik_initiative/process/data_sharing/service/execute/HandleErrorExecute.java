package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;

import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorExecute implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorExecute.class);

	private final DataSetStatusGenerator statusGenerator;
	private final boolean dicEmailEnabled;

	public HandleErrorExecute(DataSetStatusGenerator statusGenerator, boolean dicEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.dicEmailEnabled = dicEmailEnabled;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.warn("Recreating user-task 'release-data-set'");
		if (dicEmailEnabled)
			sendMail(api, variables);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE, null);
	}

	private void sendMail(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String statusCode = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "Could not provide data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' for DMS '" + dmsIdentifier
				+ "' regarding project-identifier '" + projectIdentifier + "':\n" + "- status code: " + statusCode
				+ "\n" + "- error: " + (error == null ? "none" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-data-set'.";

		api.getMailService().send(subject, message);
	}
}
