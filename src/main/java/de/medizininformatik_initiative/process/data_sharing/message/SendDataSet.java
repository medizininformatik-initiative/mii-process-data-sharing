package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.Objects;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class SendDataSet extends AbstractTaskMessageSend implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(SendDataSet.class);

	private final DataSetStatusGenerator statusGenerator;

	public SendDataSet(ProcessPluginApi api, DataSetStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		String binaryId = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE);

		Task.ParameterComponent inputDataSetReference = new Task.ParameterComponent();
		inputDataSetReference.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_REFERENCE);
		inputDataSetReference.setValue(new Reference().setType(ResourceType.Binary.name()).setReference(binaryId));

		return Stream.of(inputDataSetReference);
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
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE,
				"Send data-set failed");

		logger.warn(
				"Could not send data-set with id '{}' for project-identifier '{}' to DMS with identifier '{}' referenced in Task with id '{}' - {}",
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER),
				variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER),
				variables.getStartTask().getId(), exception.getMessage());
		throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR,
				"Send data-set - " + exception.getMessage());
	}

	@Override
	protected void addErrorMessage(Task task, String errorMessage)
	{
		// Override in order not to add error message of AbstractTaskMessageSend
	}
}
