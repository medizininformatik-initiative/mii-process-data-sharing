package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;

import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.MimeTypeHelper;

import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.service.DsfClientProvider;
import dev.dsf.bpe.v2.service.MimeTypeService;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.core.MediaType;

public class ValidateDataSetExecute implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(ValidateDataSetExecute.class);

	private final String fhirStoreId;
	private final boolean fhirBinaryStreamReadUseHapiBlobStorageOperation;

	public ValidateDataSetExecute(String fhirStoreId, boolean fhirBinaryStreamReadUseHapiBlobStorageOperation)
	{
		this.fhirStoreId = fhirStoreId;
		this.fhirBinaryStreamReadUseHapiBlobStorageOperation = fhirBinaryStreamReadUseHapiBlobStorageOperation;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		logger.info("Validating data-set for DMS '{}' and project-identifier '{}' in Task '{}'", dmsIdentifier,
				projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		try
		{
			DsfClient client = getDsfClientForFhirStore(api.getDsfClientProvider(), fhirStoreId);

			List<Resource> resources = variables.getFhirResourceList(
					ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_INITIAL_DATA_RESOURCES);
			resources.forEach(r -> validate(client, api.getFhirContext(), api.getMimeTypeService(), r));
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not validate data-set for DMS '{}' and project-identifier '{}' referenced in Task with id '{}'"
							+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + "{}", dmsIdentifier, projectIdentifier,
					task.getId(), exception.getMessage());

			String error =
					"Validating data-set failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();
			throw new ErrorBoundaryEvent(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR,
					error);
		}
	}

	private DsfClient getDsfClientForFhirStore(DsfClientProvider provider, String fhirStoreId)
	{
		return provider.getById(fhirStoreId)
				.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"));
	}

	private void validate(DsfClient client, FhirContext fhirContext, MimeTypeService mimeTypeService, Resource resource)
	{
		if (resource instanceof ListResource list)
			validateStream(client, mimeTypeService, list);
		else
			validateResource(fhirContext, mimeTypeService, resource);
	}

	private void validateResource(FhirContext fhirContext, MimeTypeService mimeTypeService, Resource resource)
	{
		String mimeType = MimeTypeHelper.getMimeType(resource);
		byte[] data = MimeTypeHelper.getData(fhirContext, resource);

		mimeTypeService.validateWithException(data, mimeType);
	}

	private void validateStream(DsfClient client, MimeTypeService mimeTypeService, ListResource list)
	{
		list.getEntry().stream().filter(ListResource.ListEntryComponent::hasItem)
				.filter(e -> e.hasExtension(ConstantsDataSharing.EXTENSION_LIST_ENTRY_MIMETYPE))
				.forEach(e -> doValidateStream(client, mimeTypeService, e));
	}

	private void doValidateStream(DsfClient client, MimeTypeService mimeTypeService,
			ListResource.ListEntryComponent listEntry)
	{
		String binaryId = listEntry.getItem().getReferenceElement().getIdPart();
		if (fhirBinaryStreamReadUseHapiBlobStorageOperation)
			binaryId += "/$binary-access-read";
		String mimetype = listEntry.getExtensionString(ConstantsDataSharing.EXTENSION_LIST_ENTRY_MIMETYPE);

		InputStream inputStream = client.readBinary(binaryId, MediaType.valueOf(mimetype));

		if (!inputStream.markSupported())
			inputStream = new BufferedInputStream(inputStream);

		mimeTypeService.validateWithException(inputStream, mimetype);
	}
}
