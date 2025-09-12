package de.medizininformatik_initiative.process.data_sharing;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.parser.IParser;
import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.util.MetadataResourceConverter;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class DataSharingProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final ProcessPluginApi api;

	private final FhirClientFactory dicFhirClientFactory;
	private final FhirClientFactory dmsFhirClientFactory;

	private final KeyProvider keyProvider;

	private final MetadataResourceConverter metadataResourceConverter;

	public DataSharingProcessPluginDeploymentStateListener(ProcessPluginApi api, FhirClientFactory dicFhirClientFactory,
			FhirClientFactory dmsFhirClientConfig, KeyProvider keyProvider,
			MetadataResourceConverter metadataResourceConverter)
	{
		this.api = api;
		this.dicFhirClientFactory = dicFhirClientFactory;
		this.dmsFhirClientFactory = dmsFhirClientConfig;
		this.keyProvider = keyProvider;
		this.metadataResourceConverter = metadataResourceConverter;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(dicFhirClientFactory, "dicFhirClientFactory");
		Objects.requireNonNull(dmsFhirClientFactory, "dmsFhirClientFactory");
		Objects.requireNonNull(keyProvider, "keyProvider");
		Objects.requireNonNull(metadataResourceConverter, "metadataResourceConverter");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		// TODO: functions metadataResourceConverter.searchAndUpdateOlderResourcesIfCurrentIsNewest added because
		// CodeSystems and StructureDefinition-Extensions with different versions cannot be used in DSF API 1.x.
		// Remove for DSF API 2.x API where CodeSystem and StructureDefinition-Extension versioning is fixed.

		metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING, CodeSystem.class,
				this::filterCodeSystemsWithNonMatchingConceptCodes, this::adaptCodeSystemsReplacingConcepts);

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING))
			dicFhirClientFactory.testConnection();

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING))
		{
			metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
					ConstantsDataSharing.PROFILE_TASK_MERGE_DATA_SHARING, StructureDefinition.class,
					this::filterStructureDefinitionProfileMergeDataSharingWithNonExistingDicIdentifierLegacySlice,
					this::adaptStructureDefinitionProfileMergeDataSharingReplacingDifferential);

			metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
					ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER, StructureDefinition.class,
					this::filterStructureDefinitionExtensionDicIdentifierWithNonMatchingUrl,
					this::adaptStructureDefinitionExtensionDicIdentifierRenameUrl);

			dmsFhirClientFactory.testConnection();
			keyProvider.createPublicKeyIfNotExists();
		}
	}

	private void adaptCodeSystemsReplacingConcepts(CodeSystem currentResource, CodeSystem olderResource)
	{
		olderResource.setConcept(currentResource.getConcept());
		updateResource(olderResource);
	}

	private boolean filterCodeSystemsWithNonMatchingConceptCodes(CodeSystem currentCodeSystem,
			CodeSystem olderCodeSystem)
	{
		return !getConceptCodes(currentCodeSystem).equals(getConceptCodes(olderCodeSystem));
	}

	private Set<String> getConceptCodes(CodeSystem codeSystem)
	{
		return codeSystem.getConcept().stream().map(CodeSystem.ConceptDefinitionComponent::getCode)
				.collect(Collectors.toSet());
	}

	private void adaptStructureDefinitionProfileMergeDataSharingReplacingDifferential(
			StructureDefinition currentResource, StructureDefinition olderResource)
	{
		StructureDefinition currentResourceOldVersion = replaceVersion(currentResource, olderResource);
		olderResource.setDifferential(currentResourceOldVersion.getDifferential());
		updateResource(olderResource);
	}

	private boolean filterStructureDefinitionProfileMergeDataSharingWithNonExistingDicIdentifierLegacySlice(
			StructureDefinition currentResource, StructureDefinition olderResource)
	{
		return olderResource.getDifferential().getElement().stream()
				.noneMatch(e -> "dic-identifier-legacy".equals(e.getSliceName()));
	}

	private void adaptStructureDefinitionExtensionDicIdentifierRenameUrl(StructureDefinition currentResource,
			StructureDefinition olderResource)
	{
		StructureDefinition currentResourceOldVersion = replaceVersion(currentResource, olderResource);
		olderResource.setDifferential(currentResourceOldVersion.getDifferential());
		updateResource(olderResource);
	}

	private boolean filterStructureDefinitionExtensionDicIdentifierWithNonMatchingUrl(
			StructureDefinition currentResource, StructureDefinition olderResource)
	{
		return olderResource.getDifferential().getElement().stream().anyMatch(e -> "Extension.url".equals(e.getPath())
				&& e.getFixed() != null && e.getFixed() instanceof UriType
				&& !ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER.equals(((UriType) e.getFixed()).getValue()));
	}

	private StructureDefinition replaceVersion(StructureDefinition currentResource, StructureDefinition olderResource)
	{
		IParser parser = api.getFhirContext().newJsonParser();
		return (StructureDefinition) parser.parseResource(parser.encodeResourceToString(currentResource)
				.replace("|" + currentResource.getVersion(), "|" + olderResource.getVersion()));
	}

	private void updateResource(MetadataResource resource)
	{
		api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(resource);
	}
}
