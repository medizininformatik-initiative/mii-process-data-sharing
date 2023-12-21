package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class SendMergedDataSet extends AbstractTaskMessageSend
{
	private static final Logger logger = LoggerFactory.getLogger(SendMergedDataSet.class);

	public SendMergedDataSet(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		String dataSetUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_URL);

		Task.ParameterComponent dataSetUrlInput = new Task.ParameterComponent();
		dataSetUrlInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);
		dataSetUrlInput.setValue(new UrlType().setValue(dataSetUrl));

		return Stream.of(dataSetUrlInput);
	}

	@Override
	protected IdType doSend(FhirWebserviceClient client, Task task)
	{
		return client.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.create(task);
	}

	@Override
	protected void handleSendTaskError(DelegateExecution execution, Variables variables, Exception exception,
			String errorMessage)
	{
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE,
				"Send merged data-set failed");

		logger.warn(
				"Could not send merged data-set with url '{}' for project-identifier '{}' to HRP referenced in Task with id '{}' - {}",
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_URL),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				variables.getStartTask().getId(), exception.getMessage());
		throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR,
				"Send merged data-set - " + exception.getMessage());
	}

	@Override
	protected void addErrorMessage(Task task, String errorMessage)
	{
		// Override in order not to add error message of AbstractTaskMessageSend
	}
}
