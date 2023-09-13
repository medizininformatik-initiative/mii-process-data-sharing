package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import jakarta.ws.rs.core.MediaType;

public class StoreDataSet extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(StoreDataSet.class);
	private final DataLogger dataLogger;

	public StoreDataSet(ProcessPluginApi api, DataLogger dataLogger)
	{
		super(api);
		this.dataLogger = dataLogger;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(dataLogger, "dataLogger");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		byte[] bundleEncrypted = (byte[]) execution
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_ENCRYPTED);

		logger.info(
				"Storing encrypted transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				dmsIdentifier, projectIdentifier, task.getId());

		try
		{
			String binaryId = storeBinary(bundleEncrypted, dmsIdentifier);
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE, binaryId);

			log(projectIdentifier, dmsIdentifier, binaryId, task.getId());
			sendMail(task, projectIdentifier, dmsIdentifier, binaryId);
		}
		catch (Exception exception)
		{
			String message = "Could not store encrypt transferable data-set for DMS '" + dmsIdentifier
					+ "' and data-sharing project '" + projectIdentifier + "' referenced in Task with id '"
					+ task.getId() + "' - " + exception.getMessage();

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE,
					message);

			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, message,
					exception);
		}
	}

	private String storeBinary(byte[] content, String dmsIdentifier)
	{
		MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_OCTET_STREAM);
		String securityContext = getSecurityContext(dmsIdentifier);

		try (InputStream in = new ByteArrayInputStream(content))
		{
			IdType created = api.getFhirWebserviceClientProvider().getLocalWebserviceClient().withMinimalReturn()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.createBinary(in, mediaType, securityContext);
			return new IdType(api.getFhirWebserviceClientProvider().getLocalWebserviceClient().getBaseUrl(),
					ResourceType.Binary.name(), created.getIdPart(), created.getVersionIdPart()).getValue();
		}
		catch (Exception exception)
		{
			logger.warn("Could not create binary - {}", exception.getMessage());
			throw new RuntimeException("Could not create binary - " + exception.getMessage(), exception);
		}
	}

	private String getSecurityContext(String dmsIdentifier)
	{
		return api.getOrganizationProvider().getOrganization(dmsIdentifier)
				.orElseThrow(() -> new RuntimeException("Could not find organization with id '" + dmsIdentifier + "'"))
				.getIdElement().toVersionless().getValue();
	}

	private void log(String projectIdentifier, String dmsIdentifier, String binaryId, String taskId)
	{
		logger.info(
				"Stored Binary with id '{}' provided for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				binaryId, dmsIdentifier, projectIdentifier, taskId);
	}

	private void sendMail(Task task, String projectIdentifier, String dmsIdentifier, String binaryId)
	{
		String subject = "Data-set provided in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING
				+ "'";
		String message = "A data-set has been successfully provided in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' and Task with id '" + task.getId()
				+ "' for DMS '" + dmsIdentifier + "' regarding project-identifier '" + projectIdentifier
				+ "' and can be accessed using the following url:\n" + "- " + binaryId;

		api.getMailService().send(subject, message);
	}
}
