package de.medizininformatik_initiative.process.data_sharing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class DataSharingProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final ProcessPluginApi api;

	private final FhirClientFactory dicFhirClientFactory;
	private final FhirClientFactory dmsFhirClientFactory;

	private final KeyProvider keyProvider;

	private final String resourcesVersion;

	private record MinorMajorVersion(int major, int minor)
	{
	}

	public DataSharingProcessPluginDeploymentStateListener(ProcessPluginApi api, FhirClientFactory dicFhirClientFactory,
			FhirClientFactory dmsFhirClientConfig, KeyProvider keyProvider, String resourcesVersion)
	{
		this.api = api;
		this.dicFhirClientFactory = dicFhirClientFactory;
		this.dmsFhirClientFactory = dmsFhirClientConfig;
		this.keyProvider = keyProvider;
		this.resourcesVersion = resourcesVersion;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(dicFhirClientFactory, "dicFhirClientFactory");
		Objects.requireNonNull(dmsFhirClientFactory, "dmsFhirClientFactory");
		Objects.requireNonNull(keyProvider, "keyProvider");
		Objects.requireNonNull(resourcesVersion, "resourcesVersion");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		// TODO: functions updateOlderCodeSystemsIfCurrentIsNewestCodeSystem and
		// updateOlderStructureDefinitionIfCurrentIsNewestStructureDefinition added because CodeSystems
		// and StructureDefinition-Extensions with different versions cannot be used in DSF API 1.x.
		// Remove for DSF API 2.x API where CodeSystem and StructureDefinition versioning is fixed.

		updateOlderResourcesIfCurrentIsNewestResource(ConstantsDataSharing.CODESYSTEM_DATA_SHARING, CodeSystem.class,
				adaptCodeSystems());

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING))
			dicFhirClientFactory.testConnection();

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING))
		{
			updateOlderResourcesIfCurrentIsNewestResource(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER,
					StructureDefinition.class, adaptStructureDefinitionExtensions());

			dmsFhirClientFactory.testConnection();
			keyProvider.createPublicKeyIfNotExists();
		}
	}

	private <T extends MetadataResource> void updateOlderResourcesIfCurrentIsNewestResource(String url, Class<T> type,
			BiConsumer<T, List<T>> converter)
	{
		Bundle searchResult = search(type, url);
		List<T> allResources = extractResources(searchResult, type, url);

		T currentResource = filterCurrentResource(allResources, type, url);
		List<T> olderNewerResources = filterOlderNewerResources(allResources);

		if (currentIsNewestResource(olderNewerResources))
		{
			converter.accept(currentResource, olderNewerResources);
		}
	}

	private Bundle search(Class<? extends Resource> type, String url)
	{
		return api.getFhirWebserviceClientProvider().getLocalWebserviceClient().search(type,
				Map.of("url", List.of(url)));
	}

	private <T extends MetadataResource> List<T> extractResources(Bundle bundle, Class<T> type, String url)
	{
		return bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource).filter(type::isInstance).map(type::cast)
				.filter(m -> url.equals(m.getUrl())).toList();
	}

	private <T extends MetadataResource> T filterCurrentResource(List<T> all, Class<T> type, String codeSystemUrl)
	{
		return all.stream().filter(m -> resourcesVersion.equals(m.getVersion())).findFirst().orElseThrow(
				() -> new RuntimeException(type.getSimpleName() + " " + codeSystemUrl + "|" + resourcesVersion));
	}

	private <T extends MetadataResource> List<T> filterOlderNewerResources(List<T> all)
	{
		return all.stream().filter(c -> !resourcesVersion.equals(c.getVersion())).toList();
	}

	private boolean currentIsNewestResource(List<? extends MetadataResource> olderNewerResources)
	{
		return olderNewerResources.stream().noneMatch(this::isNewerResource);
	}

	private <T extends MetadataResource> boolean isNewerResource(T resource)
	{
		MinorMajorVersion current = getMajorMinorVersion(resourcesVersion);
		MinorMajorVersion olderNewer = getMajorMinorVersion(resource.getVersion());

		return current.major <= olderNewer.major && current.minor < olderNewer.minor;
	}

	private MinorMajorVersion getMajorMinorVersion(String version)
	{
		if (version.matches("\\d\\.\\d"))
		{
			String[] minorMajor = version.split("\\.");
			return new MinorMajorVersion(Integer.parseInt(minorMajor[0]), Integer.parseInt(minorMajor[1]));
		}

		throw new RuntimeException("Fhir resource version " + version + " does not match regex \\d\\.\\d");
	}

	private BiConsumer<CodeSystem, List<CodeSystem>> adaptCodeSystems()
	{
		return (currentResource, olderResources) ->
		{
			List<CodeSystem> codeSystemsWithNonMatchingConceptCodes = filterCodeSystemsWithNonMatchingConceptCodesAndAdaptToCurrentCodeSystemConceptCodes(
					currentResource, olderResources);
			updateResources(codeSystemsWithNonMatchingConceptCodes);
		};
	}

	private List<CodeSystem> filterCodeSystemsWithNonMatchingConceptCodesAndAdaptToCurrentCodeSystemConceptCodes(
			CodeSystem currentCodeSystem, List<CodeSystem> olderCodeSystems)
	{
		Set<String> currentConceptCodes = getConceptCodes(currentCodeSystem);
		return olderCodeSystems.stream().filter(c -> !currentConceptCodes.equals(getConceptCodes(c)))
				.map(c -> c.setConcept(currentCodeSystem.getConcept())).toList();
	}

	private Set<String> getConceptCodes(CodeSystem codeSystem)
	{
		return codeSystem.getConcept().stream().map(CodeSystem.ConceptDefinitionComponent::getCode)
				.collect(Collectors.toSet());
	}

	private BiConsumer<StructureDefinition, List<StructureDefinition>> adaptStructureDefinitionExtensions()
	{
		return (currentResource, olderResources) ->
		{
			List<StructureDefinition> structureDefinitionsWithNonMatchingUrls = filterStructureDefinitionsWithNonMatchingUrlAndAdaptToCurrentUrl(
					olderResources, currentResource.getUrl());
			updateResources(structureDefinitionsWithNonMatchingUrls);
		};
	}

	private List<StructureDefinition> filterStructureDefinitionsWithNonMatchingUrlAndAdaptToCurrentUrl(
			List<StructureDefinition> olderStructureDefinitions, String url)
	{
		List<StructureDefinition> nonMatchingUrl = olderStructureDefinitions.stream()
				.filter(s -> s.getDifferential().getElement().stream()
						.anyMatch(e -> "Extension.url".equals(e.getPath()) && e.getFixed() != null
								&& e.getFixed() instanceof UriType && !url.equals(((UriType) e.getFixed()).getValue())))
				.toList();

		nonMatchingUrl.forEach(s -> s.getDifferential().getElement().stream()
				.filter(e -> "Extension.url".equals(e.getPath())).forEach(e -> e.setFixed(new UriType(url))));

		return nonMatchingUrl;

	}

	private void updateResources(List<? extends MetadataResource> resources)
	{
		resources.forEach(m -> api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(m));
	}
}
