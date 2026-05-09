package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.variables.DataResource;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.BasicDsfClient;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.core.MediaType;

public class DownloadDataSet implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadDataSet.class);

	private final DataSetStatusGenerator statusGenerator;
	private final boolean fhirBinaryStreamWriteEnabled;

	public DownloadDataSet(DataSetStatusGenerator statusGenerator, boolean fhirBinaryStreamWriteEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.fhirBinaryStreamWriteEnabled = fhirBinaryStreamWriteEnabled;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getLatestTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		IdType documentReferenceLocation = getDocumentReferenceLocation(api.getTaskHelper(), task, sendingOrganization,
				projectIdentifier);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DOCUMENT_REFERENCE_LOCATION,
				documentReferenceLocation.getValue());

		logger.info(
				"Downloading data-set from organization '{}' for project-identifier '{}' in Task '{}' (DocumentReference '{}' and its encrypted attachments)",
				sendingOrganization, projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task),
				documentReferenceLocation.getValue());

		try
		{
			DocumentReference documentReference = readDocumentReference(api, documentReferenceLocation,
					sendingOrganization, projectIdentifier, task);
			Stream<DataResource> attachments = readAttachments(api, documentReference);
			List<Resource> resources = getResources(attachments);

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER, projectIdentifier);
			variables.setFhirResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DOCUMENT_REFERENCE,
					documentReference);
			variables.setFhirResourceList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DATA_RESOURCES,
					resources);
		}
		catch (Exception exception)
		{
			String message = "Download data-set failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
					+ exception.getMessage();
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createDataSetStatusOutput(
					api.getProcessPluginDefinition().getResourceVersion(),
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING, api.getProcessPluginDefinition().getResourceVersion(),
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, message));
			variables.updateTask(task);

			throw new ErrorBoundaryEvent(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR, message);
		}
	}

	private IdType getDocumentReferenceLocation(TaskHelper helper, Task task, String sendingOrganization,
			String projectIdentifier)
	{
		List<String> dataSetReferences = helper
				.getInputParameters(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_LOCATION, Reference.class)
				.map(Task.ParameterComponent::getValue).filter(i -> i instanceof Reference).map(i -> (Reference) i)
				.filter(Reference::hasReference).map(Reference::getReference).toList();

		if (dataSetReferences.isEmpty())
			throw new IllegalArgumentException("Task.input:document-reference-location missing");

		if (dataSetReferences.size() > 1)
			logger.warn(
					"Found {} DocumentReference locations from organization '{}' and project-identifier '{}' in Task '{}', using only the first",
					dataSetReferences.size(), sendingOrganization, projectIdentifier,
					helper.getLocalVersionlessAbsoluteUrl(task));

		return new IdType(dataSetReferences.getFirst());
	}

	private DocumentReference readDocumentReference(ProcessPluginApi api, IdType documentReferenceLocation,
			String sendingOrganization, String projectIdentifier, Task task)
	{
		DocumentReference documentReference = api.getDsfClientProvider()
				.getByEndpointUrl(documentReferenceLocation.getBaseUrl())
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
						DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN))
				.read(DocumentReference.class, documentReferenceLocation.getIdPart(),
						documentReferenceLocation.getVersionIdPart());

		api.getDataLogger()
				.log("DocumentReference with project-identifier '" + projectIdentifier + "from organization '"
						+ sendingOrganization + "' and Task '"
						+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'", documentReference);

		return documentReference;
	}

	private Stream<DataResource> readAttachments(ProcessPluginApi api, DocumentReference documentReference)
	{
		return documentReference.getContent().stream()
				.filter(DocumentReference.DocumentReferenceContentComponent::hasAttachment)
				.map(DocumentReference.DocumentReferenceContentComponent::getAttachment)
				.map(a -> readAttachment(api, a));
	}

	private DataResource readAttachment(ProcessPluginApi api, Attachment attachment)
	{
		IdType attachmentId = new IdType(attachment.getUrl());

		BasicDsfClient client = api.getDsfClientProvider().getByEndpointUrl(attachmentId.getBaseUrl()).withRetry(
				ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN));

		String mimetype = getAttachmentMimeType(attachment);
		if (fhirBinaryStreamWriteEnabled && !isMimetypeFhir(mimetype))
		{
			return DataResource.of(attachmentId, mimetype);
		}
		else
		{
			try (InputStream binary = readBinaryResource(client, attachmentId.getIdPart(),
					attachmentId.getVersionIdPart()))
			{
				return DataResource
						.of(new Binary().setData(binary.readAllBytes()).setContentType(attachment.getContentType()));
			}
			catch (Exception exception)
			{
				throw new RuntimeException("Downloading attachment failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
						+ exception.getMessage(), exception);
			}
		}
	}

	private String getAttachmentMimeType(Attachment attachment)
	{
		return Optional.of(attachment).filter(Attachment::hasContentType).map(Attachment::getContentType)
				.orElseThrow(() -> new IllegalArgumentException(
						"Could not find any attachment contentType (mimeType) in DocumentReference"));
	}

	private InputStream readBinaryResource(BasicDsfClient client, String id, String version)
	{
		MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_OCTET_STREAM);
		if (version != null && !version.isEmpty())
			return client.readBinary(id, version, mediaType);
		else
			return client.readBinary(id, mediaType);
	}

	private List<Resource> getResources(Stream<DataResource> dataResources)
	{
		return dataResources.map(DataResource::toResource).filter(Objects::nonNull).toList();
	}

	private boolean isMimetypeFhir(String mimetype)
	{
		return "application/fhir+xml".equals(mimetype) || "application/fhir+json".equals(mimetype);
	}
}
