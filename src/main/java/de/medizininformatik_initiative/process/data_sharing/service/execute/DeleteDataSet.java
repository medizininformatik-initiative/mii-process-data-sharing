package de.medizininformatik_initiative.process.data_sharing.service.execute;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.BasicFhirWebserviceClient;

public class DeleteDataSet extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(DeleteDataSet.class);

	public DeleteDataSet(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		IdType binaryId = new IdType(
				(String) execution.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE));

		logger.info(
				"Permanently deleting encrypted Binary with id '{}' provided for DMS '{}' and data-sharing project '{}' "
						+ "referenced in Task with id '{}'",
				binaryId.getValue(), dmsIdentifier, projectIdentifier, task.getId());

		try
		{
			deletePermanently(binaryId);
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not permanently delete data-set for DMS '{}' and project-identifier '{}' referenced in Task with id '{}' - {}",
					dmsIdentifier, projectIdentifier, task.getId(), exception.getMessage());

			// do not throw exception as process has to continue in error handling
		}
	}

	private void deletePermanently(IdType binaryId)
	{
		BasicFhirWebserviceClient client = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

		client.delete(Binary.class, binaryId.getIdPart());
		client.deletePermanently(Binary.class, binaryId.getIdPart());
	}
}
