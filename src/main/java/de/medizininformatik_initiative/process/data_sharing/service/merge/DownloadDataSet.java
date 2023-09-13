package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.BasicFhirWebserviceClient;
import jakarta.ws.rs.core.MediaType;

public class DownloadDataSet extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadDataSet.class);

	private final DataSetStatusGenerator statusGenerator;

	public DownloadDataSet(ProcessPluginApi api, DataSetStatusGenerator statusGenerator)
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
	protected void doExecute(DelegateExecution execution, Variables variables) throws Exception
	{
		Task task = variables.getLatestTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		IdType dataSetReference = getDataSetReference(task);

		logger.info(
				"Downloading data-set with id '{}' from organization '{}' for data-sharing project '{}' in Task with id '{}'",
				dataSetReference.getValue(), sendingOrganization, projectIdentifier, task.getId());

		try
		{
			byte[] bundleEncrypted = readDataSet(dataSetReference);
			variables.setByteArray(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_ENCRYPTED, bundleEncrypted);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createDataSetStatusOutput(
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "Download data-set failed"));
			variables.updateTask(task);

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
					"Download data-set failed");

			logger.warn(
					"Could not download data-set with id '{}}' from organization '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					dataSetReference.getValue(), sendingOrganization, projectIdentifier, task.getId(),
					exception.getMessage());

			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR,
					"Download data-set - " + exception.getMessage());
		}
	}

	private IdType getDataSetReference(Task task)
	{
		List<String> dataSetReferences = api.getTaskHelper()
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_REFERENCE, Reference.class)
				.filter(Reference::hasReference).map(Reference::getReference).toList();

		if (dataSetReferences.size() < 1)
			throw new IllegalArgumentException("No data-set reference present in Task with id '" + task.getId() + "'");

		if (dataSetReferences.size() > 1)
			logger.warn("Found {} data-set references in Task with id '{}', using only the first",
					dataSetReferences.size(), task.getId());

		return new IdType(dataSetReferences.get(0));
	}

	private byte[] readDataSet(IdType dataSetReference)
	{
		BasicFhirWebserviceClient client = api.getFhirWebserviceClientProvider()
				.getWebserviceClient(dataSetReference.getBaseUrl())
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

		try (InputStream binary = readBinaryResource(client, dataSetReference.getIdPart(),
				dataSetReference.getVersionIdPart()))
		{
			return binary.readAllBytes();
		}
		catch (Exception exception)
		{
			logger.warn("Downloading Binary with id '{}' failed - {}", dataSetReference.getValue(),
					exception.getMessage());
			throw new RuntimeException("Downloading Binary with id '" + dataSetReference.getValue() + "' failed - "
					+ exception.getMessage(), exception);
		}
	}

	private InputStream readBinaryResource(BasicFhirWebserviceClient client, String id, String version)
	{
		if (version != null && !version.isEmpty())
			return client.readBinary(id, version, MediaType.valueOf(MediaType.APPLICATION_OCTET_STREAM));
		else
			return client.readBinary(id, MediaType.valueOf(MediaType.APPLICATION_OCTET_STREAM));
	}
}
