package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.crypto.RsaAesGcmUtil;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class DecryptDataSet extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DecryptDataSet.class);
	private final KeyProvider keyProvider;
	private final DataLogger dataLogger;
	private final DataSetStatusGenerator statusGenerator;

	public DecryptDataSet(ProcessPluginApi api, KeyProvider keyProvider, DataLogger dataLogger,
			DataSetStatusGenerator statusGenerator)
	{
		super(api);

		this.keyProvider = keyProvider;
		this.dataLogger = dataLogger;
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(keyProvider, "keyProvider");
		Objects.requireNonNull(dataLogger, "dataLogger");
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getLatestTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		String localOrganization = api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue is empty"));

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		byte[] bundleEncrypted = variables
				.getByteArray(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_ENCRYPTED);

		logger.info("Decrypting data-set from organization '{}' for data-sharing project '{}' in Task with id '{}'",
				sendingOrganization, projectIdentifier, task.getId());

		try
		{
			Bundle bundleDecrypted = decryptBundle(keyProvider.getPrivateKey(), bundleEncrypted, sendingOrganization,
					localOrganization);

			dataLogger.logResource("Decrypted Transfer Bundle", bundleDecrypted);

			variables.setResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET, bundleDecrypted);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createDataSetStatusOutput(
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "Decrypt data-set failed"));
			variables.updateTask(task);

			logger.warn(
					"Could not decrypt data-set with id '{}' from organization '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_REFERENCE),
					sendingOrganization, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Decrypt data-set failed" + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
					error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, error,
					exception);
		}
	}

	private Bundle decryptBundle(PrivateKey privateKey, byte[] bundleEncrypted, String sendingOrganization,
			String receivingOrganization)
	{
		try
		{
			byte[] bundleDecrypted = RsaAesGcmUtil.decrypt(privateKey, bundleEncrypted, sendingOrganization,
					receivingOrganization);
			String bundleString = new String(bundleDecrypted, StandardCharsets.UTF_8);
			return (Bundle) FhirContext.forR4().newXmlParser().parseResource(bundleString);
		}
		catch (Exception exception)
		{
			logger.warn("Could not decrypt data-set - {}", exception.getMessage());
			throw new RuntimeException("Could not decrypt received data-set", exception);
		}
	}
}
