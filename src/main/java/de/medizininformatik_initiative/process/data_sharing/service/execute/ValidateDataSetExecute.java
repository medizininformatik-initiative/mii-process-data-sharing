package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.mimetype.MimeTypeHelper;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class ValidateDataSetExecute extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(ValidateDataSetExecute.class);

	private final MimeTypeHelper mimeTypeHelper;
	private final FhirClientFactory fhirClientFactory;
	private final boolean fhirBinaryStreamReadUseHapiBlobStorageOperation;

	public ValidateDataSetExecute(ProcessPluginApi api, MimeTypeHelper mimeTypeHelper,
			FhirClientFactory fhirClientFactory, boolean fhirBinaryStreamReadUseHapiBlobStorageOperation)
	{
		super(api);
		this.mimeTypeHelper = mimeTypeHelper;
		this.fhirClientFactory = fhirClientFactory;
		this.fhirBinaryStreamReadUseHapiBlobStorageOperation = fhirBinaryStreamReadUseHapiBlobStorageOperation;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(mimeTypeHelper, "mimeTypeHelper");
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		logger.info("Validating data-set for DMS '{}' and project-identifier '{}' referenced in Task with id '{}'",
				dmsIdentifier, projectIdentifier, variables.getStartTask().getId());

		try
		{
			List<Resource> resources = variables
					.getResourceList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_INITIAL_DATA_RESOURCES);
			resources.forEach(this::validate);
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not validate data-set for DMS '{}' and project-identifier '{}' referenced in Task with id '{}' - {}",
					dmsIdentifier, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Validating data-set failed - " + exception.getMessage();
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, error,
					exception);
		}
	}

	private void validate(Resource resource)
	{
		if (resource instanceof ListResource list)
			validateStream(list);
		else
			validateResource(resource);
	}

	private void validateResource(Resource resource)
	{
		String mimeType = mimeTypeHelper.getMimeType(resource);
		byte[] data = mimeTypeHelper.getData(resource);

		mimeTypeHelper.validate(data, mimeType);
	}

	private void validateStream(ListResource list)
	{
		list.getEntry().stream().filter(ListResource.ListEntryComponent::hasItem)
				.filter(e -> e.hasExtension(ConstantsDataSharing.EXTENSION_LIST_ENTRY_MIMETYPE))
				.forEach(this::doValidateStream);
	}

	private void doValidateStream(ListResource.ListEntryComponent listEntry)
	{
		IdType url = (IdType) listEntry.getItem().getReferenceElement();
		String mimetype = listEntry.getExtensionString(ConstantsDataSharing.EXTENSION_LIST_ENTRY_MIMETYPE);

		InputStream inputStream = fhirClientFactory.getBinaryStreamFhirClient().read(url, mimetype,
				fhirBinaryStreamReadUseHapiBlobStorageOperation);

		if (!inputStream.markSupported())
			inputStream = new BufferedInputStream(inputStream);

		try
		{
			mimeTypeHelper.validate(inputStream, mimetype);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
