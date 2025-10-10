package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
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
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
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
	private final DataLogger dataLogger;
	private final boolean fhirBinaryStreamWriteEnabled;

	public DownloadDataSet(ProcessPluginApi api, DataSetStatusGenerator statusGenerator,
			boolean fhirBinaryStreamWriteEnabled, DataLogger dataLogger)
	{
		super(api);
		this.statusGenerator = statusGenerator;
		this.fhirBinaryStreamWriteEnabled = fhirBinaryStreamWriteEnabled;
		this.dataLogger = dataLogger;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "statusGenerator");
		Objects.requireNonNull(dataLogger, "dataLogger");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getLatestTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		IdType documentReferenceLocation = getDocumentReferenceLocation(task, sendingOrganization, projectIdentifier);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DOCUMENT_REFERENCE_LOCATION,
				documentReferenceLocation.getValue());

		logger.info(
				"Downloading data-set from organization '{}' for project-identifier '{}' referenced in Task with id '{}' (DocumentReference with id '{}' and its encrypted attachments)",
				sendingOrganization, projectIdentifier, task.getId(), documentReferenceLocation.getValue());

		try
		{
			DocumentReference documentReference = readDocumentReference(documentReferenceLocation, sendingOrganization,
					projectIdentifier, task.getId());
			Stream<DataResource> attachments = readAttachments(documentReference);
			List<Resource> resources = getResources(attachments, sendingOrganization, projectIdentifier, task.getId());

			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER, projectIdentifier);
			variables.setResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DOCUMENT_REFERENCE,
					documentReference);
			variables.setResourceList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_TRANSFER_DATA_RESOURCES, resources);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createDataSetStatusOutput(
					ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "Download data-set failed"));
			variables.updateTask(task);

			logger.warn(
					"Could not download data-set from organization '{}' for project-identifier '{}' referenced in Task with id '{}' (DocumentReference with id '{}' and its encrypted attachments) - {}",
					sendingOrganization, projectIdentifier, task.getId(), documentReferenceLocation.getValue(),
					exception.getMessage());

			String error = "Download data-set failed - " + exception.getMessage();
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, error,
					exception);
		}
	}

	private IdType getDocumentReferenceLocation(Task task, String sendingOrganization, String projectIdentifier)
	{
		List<String> dataSetReferences = api.getTaskHelper()
				.getInputParameters(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_LOCATION, Reference.class)
				.map(Task.ParameterComponent::getValue).filter(i -> i instanceof Reference).map(i -> (Reference) i)
				.filter(Reference::hasReference).map(Reference::getReference).toList();

		if (dataSetReferences.isEmpty())
			throw new IllegalArgumentException("No DocumentReference reference present in Task.input");

		if (dataSetReferences.size() > 1)
			logger.warn(
					"Found {} DocumentReference references from organization '{}' for project-identifier '{}' referenced in Task with id '{}', using only the first",
					dataSetReferences.size(), sendingOrganization, projectIdentifier, task.getId());

		return new IdType(dataSetReferences.get(0));
	}

	private DocumentReference readDocumentReference(IdType documentReferenceLocation, String sendingOrganization,
			String projectIdentifier, String taskId)
	{
		DocumentReference documentReference = api.getFhirWebserviceClientProvider()
				.getWebserviceClient(documentReferenceLocation.getBaseUrl())
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.read(DocumentReference.class, documentReferenceLocation.getIdPart(),
						documentReferenceLocation.getVersionIdPart());

		dataLogger
				.logResource(
						"DocumentReference from organization '" + sendingOrganization + "' for project-identifier '"
								+ projectIdentifier + "' referenced in Task with id '" + taskId + "'",
						documentReference);

		return documentReference;
	}

	private Stream<DataResource> readAttachments(DocumentReference documentReference)
	{
		return documentReference.getContent().stream()
				.filter(DocumentReference.DocumentReferenceContentComponent::hasAttachment)
				.map(DocumentReference.DocumentReferenceContentComponent::getAttachment).map(this::readAttachment);
	}

	private DataResource readAttachment(Attachment attachment)
	{
		IdType attachmentId = new IdType(attachment.getUrl());

		BasicFhirWebserviceClient client = api.getFhirWebserviceClientProvider()
				.getWebserviceClient(attachmentId.getBaseUrl())
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

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
				throw new RuntimeException("Downloading attachment failed - " + exception.getMessage(), exception);
			}
		}
	}

	private String getAttachmentMimeType(Attachment attachment)
	{
		return Optional.of(attachment).filter(Attachment::hasContentType).map(Attachment::getContentType)
				.orElseThrow(() -> new IllegalArgumentException(
						"Could not find any attachment contentType (mimeType) in DocumentReference"));
	}

	private InputStream readBinaryResource(BasicFhirWebserviceClient client, String id, String version)
	{
		MediaType mediaType = MediaType.valueOf(MediaType.APPLICATION_OCTET_STREAM);
		if (version != null && !version.isEmpty())
			return client.readBinary(id, version, mediaType);
		else
			return client.readBinary(id, mediaType);
	}

	private List<Resource> getResources(Stream<DataResource> dataResources, String sendingOrganization,
			String projectIdentifier, String taskId)
	{
		return dataResources.map(DataResource::toResource).filter(Objects::nonNull)
				.peek(r -> dataLogger.logResource(
						"Read attachment from organization '" + sendingOrganization + "' for project-identifier '"
								+ projectIdentifier + "' referenced in Task with id '" + taskId + "'",
						r))
				.toList();
	}

	private boolean isMimetypeFhir(String mimetype)
	{
		return "application/fhir+xml".equals(mimetype) || "application/fhir+json".equals(mimetype);
	}
}
