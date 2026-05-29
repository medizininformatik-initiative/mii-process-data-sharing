package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.variables.DataResource;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.service.DsfClientProvider;
import dev.dsf.bpe.v2.variables.Variables;

public class ReadDataSet implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(ReadDataSet.class);

	private final String fhirStoreId;
	private final boolean fhirBinaryStreamReadEnabled;

	public ReadDataSet(String fhirStoreId, boolean fhirBinaryStreamReadEnabled)
	{
		this.fhirStoreId = fhirStoreId;
		this.fhirBinaryStreamReadEnabled = fhirBinaryStreamReadEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		logger.info("Reading data-set for DMS '{}' and project-identifier '{}' in Task '{}'", dmsIdentifier,
				projectIdentifier, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		try
		{
			DsfClient client = getDsfClientForFhirStore(api.getDsfClientProvider(), fhirStoreId);
			DocumentReference documentReference = readDocumentReference(api, client, task, dmsIdentifier,
					projectIdentifier);

			Stream<DataResource> attachments = readAttachments(client, documentReference);
			List<Resource> resources = getResources(attachments);

			variables.setFhirResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_INITIAL_DOCUMENT_REFERENCE,
					documentReference);
			variables.setFhirResourceList(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_INITIAL_DATA_RESOURCES,
					resources);
		}
		catch (Exception exception)
		{
			String error = "Reading data-set failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();
			throw new ErrorBoundaryEvent(ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_NOT_SENT, error);
		}
	}

	private DsfClient getDsfClientForFhirStore(DsfClientProvider provider, String fhirStoreId)
	{
		return provider.getById(fhirStoreId)
				.orElseThrow(() -> new RuntimeException("DSF client config '" + fhirStoreId + "' not configured"));
	}

	private DocumentReference readDocumentReference(ProcessPluginApi api, DsfClient client, Task task,
			String dmsIdentifier, String projectIdentifier)
	{
		String projectIdentifierWithSystem = ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER + "|"
				+ projectIdentifier;
		Map<String, List<String>> identifierSearchParam = Map.of("identifier", List.of(projectIdentifierWithSystem));

		List<DocumentReference> documentReferences = client.search(DocumentReference.class, identifierSearchParam)
				.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(r -> r instanceof DocumentReference).map(r -> (DocumentReference) r).toList();

		if (documentReferences.isEmpty())
			throw new RuntimeException("Could not find DocumentReference with project-identifier '" + projectIdentifier
					+ "' for DMS  '" + dmsIdentifier + "'");

		DocumentReference documentReference = documentReferences.getFirst();

		if (documentReferences.size() > 1)
			logger.warn("Found {} DocumentReferences, using the first '{}' for Task '{}'", documentReferences.size(),
					documentReference.getIdElement().getValue(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		api.getDataLogger()
				.log("DocumentReference with project-identifier '" + projectIdentifier + "' for DMS  '" + dmsIdentifier
						+ "' and Task '" + api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'",
						documentReference);
		return documentReference;
	}

	private Stream<DataResource> readAttachments(DsfClient client, DocumentReference documentReference)
	{
		return Stream.of(documentReference).filter(DocumentReference::hasContent)
				.flatMap(dr -> dr.getContent().stream())
				.filter(DocumentReference.DocumentReferenceContentComponent::hasAttachment)
				.map(DocumentReference.DocumentReferenceContentComponent::getAttachment)
				.map(a -> readAttachment(client, a));
	}

	private DataResource readAttachment(DsfClient client, Attachment attachment)
	{
		String url = getAttachmentUrl(attachment);
		IdType urlIdType = checkValidKdsFhirStoreUrlAndGetIdType(client, url);

		if (ResourceType.Binary.name().equals(urlIdType.getResourceType()) && fhirBinaryStreamReadEnabled)
		{
			String mimetype = getAttachmentMimeType(attachment);
			return DataResource.of(urlIdType, mimetype);
		}
		else
		{
			Resource resource = client.read(urlIdType.getResourceType(), urlIdType.getIdPart());
			return DataResource.of(resource);
		}
	}

	private String getAttachmentUrl(Attachment attachment)
	{
		return Optional.of(attachment).filter(Attachment::hasUrl).map(Attachment::getUrl)
				.orElseThrow(() -> new IllegalArgumentException("DocumentReference.content.attachment.url missing"));
	}

	private String getAttachmentMimeType(Attachment attachment)
	{
		return Optional.of(attachment).filter(Attachment::hasContentType).map(Attachment::getContentType).orElseThrow(
				() -> new IllegalArgumentException("DocumentReference.content.attachment.contentType missing"));
	}

	private IdType checkValidKdsFhirStoreUrlAndGetIdType(DsfClient client, String url)
	{
		IdType idType = new IdType(url);

		// expecting no baseUrl or, baseUrl equal to client baseUrl
		boolean hasValidBaseUrl = !idType.hasBaseUrl() || client.getBaseUrl().equals(idType.getBaseUrl());
		boolean isResourceReference = idType.hasResourceType() && idType.hasIdPart();

		if (hasValidBaseUrl && isResourceReference)
			return idType;
		else
			throw new RuntimeException("DocumentReference.content.attachment.url '" + url
					+ "' is not valid (baseUrl must match client baseUrl, resource type must be set, id must be set) ");
	}

	private List<Resource> getResources(Stream<DataResource> dataResources)
	{
		List<Resource> resources = dataResources.map(DataResource::toResource).filter(Objects::nonNull).toList();
		return combineListResources(resources);
	}

	private List<Resource> combineListResources(List<Resource> resources)
	{
		ListResource listResource = new ListResource()
				.setEntry(resources.stream().filter(r -> r instanceof ListResource).map(l -> ((ListResource) l))
						.flatMap(l -> l.getEntry().stream()).toList());

		Stream<Resource> notListResources = resources.stream().filter(r -> !(r instanceof ListResource));

		if (!listResource.getEntry().isEmpty())
			return Stream.concat(notListResources, Stream.of(listResource)).toList();
		else
			return notListResources.toList();
	}
}
