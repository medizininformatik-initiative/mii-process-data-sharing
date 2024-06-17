package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.MimeTypeHelper;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class ValidateDataSetExecute extends AbstractServiceDelegate implements InitializingBean
{
	private final MimeTypeHelper mimeTypeHelper;

	private static final Logger logger = LoggerFactory.getLogger(ValidateDataSetExecute.class);

	public ValidateDataSetExecute(ProcessPluginApi api, MimeTypeHelper mimeTypeHelper)
	{
		super(api);
		this.mimeTypeHelper = mimeTypeHelper;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(mimeTypeHelper, "mimeTypeHelper");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		logger.info("Validating data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				dmsIdentifier, projectIdentifier, task.getId());

		try
		{
			Resource resource = (Resource) execution
					.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_RESOURCE);

			String mimeType = mimeTypeHelper.getMimeType(resource);
			byte[] data = mimeTypeHelper.getData(resource);

			mimeTypeHelper.validate(data, mimeType);
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not validate data-set for DMS '{}}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					dmsIdentifier, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Validate data-set failed - " + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE, error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, error,
					exception);
		}
	}
}

