package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class CommunicateReceivedDataSet implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateReceivedDataSet.class);

	private final boolean hrpEmailEnabled;

	public CommunicateReceivedDataSet(boolean hrpEmailEnabled)
	{
		this.hrpEmailEnabled = hrpEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String organizationIdentifier = getOrganizationIdentifier(api.getTaskHelper(), latestTask);

		logger.info("DMS '{}' received data-set from organization '{}' and project-identifier '{}' in Task '{}'",
				dmsIdentifier, organizationIdentifier, projectIdentifier,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(startTask));
		if (hrpEmailEnabled)
			sendMail(api, latestTask, dmsIdentifier, organizationIdentifier, projectIdentifier);

		addStartTaskOutputReceivedDataSet(variables, organizationIdentifier);
		updateTask(api.getDsfClientProvider().getLocal(), startTask, variables);

		removeOrganizationFromTargets(organizationIdentifier, variables);
		completeLatestTask(api.getDsfClientProvider().getLocal(), latestTask, variables);
	}

	private String getOrganizationIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task,
						new Coding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING).setCode(
								ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER),
						Reference.class)
				.map(Reference::getIdentifier).map(Identifier::getValue).orElse("unknown");
	}

	private void sendMail(ProcessPluginApi api, Task task, String dmsIdentifier, String organizationIdentifier,
			String projectIdentifier)
	{
		String subject = "Data-set successfully delivered in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "A data-set has been successfully delivered and retrieved in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' for Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' from DIC '" + organizationIdentifier
				+ "' to DMS '" + dmsIdentifier + "' regarding project-identifier '" + projectIdentifier + "'";

		api.getMailService().send(subject, message);
	}

	private void addStartTaskOutputReceivedDataSet(Variables variables, String organizationIdentifier)
	{
		Task task = variables.getStartTask();
		task.addOutput()
				.setValue(new Reference()
						.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(organizationIdentifier))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_RECEIVED);
		variables.updateTask(task);
	}

	private void removeOrganizationFromTargets(String organizationIdentifier, Variables variables)
	{
		List<Target> targets = variables.getTargets().getEntries();
		List<Target> targetsWithoutReceivedIdentifier = targets.stream()
				.filter(t -> !organizationIdentifier.equals(t.getOrganizationIdentifierValue())).toList();
		Targets newTargets = variables.createTargets(targetsWithoutReceivedIdentifier);
		variables.setTargets(newTargets);
	}

	private void completeLatestTask(DsfClient client, Task task, Variables variables)
	{
		task.setStatus(Task.TaskStatus.COMPLETED);
		updateTask(client, task, variables);
	}

	private void updateTask(DsfClient client, Task task, Variables variables)
	{
		client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(task);
		variables.updateTask(task);
	}
}
