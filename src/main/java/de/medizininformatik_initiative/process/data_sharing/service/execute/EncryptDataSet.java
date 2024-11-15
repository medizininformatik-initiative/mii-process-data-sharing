package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.crypto.RsaAesGcmUtil;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class EncryptDataSet extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(EncryptDataSet.class);

	private final KeyProvider keyProvider;

	public EncryptDataSet(ProcessPluginApi api, KeyProvider keyProvider)
	{
		super(api);
		this.keyProvider = keyProvider;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(keyProvider, "keyProvider");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		logger.info(
				"Encrypting transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}'",
				dmsIdentifier, projectIdentifier, task.getId());

		try
		{
			String dmsUrl = variables.getTarget().getEndpointUrl();
			String localIdentifier = api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
					.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue is empty"));

			Bundle toEncrypt = variables.getResource(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET);

			PublicKey publicKey = readPublicKey(dmsIdentifier, dmsUrl);
			byte[] encrypted = encrypt(publicKey, toEncrypt, localIdentifier, dmsIdentifier);

			variables.setByteArray(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SET_ENCRYPTED, encrypted);
		}
		catch (Exception exception)
		{
			logger.warn(
					"Could not encrypt transferable data-set for DMS '{}' and data-sharing project '{}' referenced in Task with id '{}' - {}",
					dmsIdentifier, projectIdentifier, task.getId(), exception.getMessage());

			String error = "Encrypt transferable data-set failed - " + exception.getMessage();
			variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE, error);
			throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, error,
					exception);
		}
	}

	private PublicKey readPublicKey(String dmsIdentifier, String dmsUrl)
	{
		Optional<Bundle> publicKeyBundleOptional = keyProvider.readPublicKeyIfExists(dmsUrl);

		if (publicKeyBundleOptional.isEmpty())
			throw new IllegalStateException(
					"Could not find PublicKey Bundle of organization with identifier '" + dmsIdentifier + "'");

		logger.debug("Downloaded PublicKey Bundle from organization with identifier '{}'", dmsIdentifier);

		Bundle publicKeyBundle = publicKeyBundleOptional.get();
		DocumentReference documentReference = getDocumentReference(publicKeyBundle);
		Binary binary = getBinary(publicKeyBundle);

		PublicKey publicKey = getPublicKey(binary, publicKeyBundle.getId());
		checkHash(documentReference, publicKey);

		return publicKey;
	}

	private DocumentReference getDocumentReference(Bundle bundle)
	{
		List<DocumentReference> documentReferences = bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource).filter(r -> r instanceof DocumentReference)
				.map(r -> (DocumentReference) r).toList();

		if (documentReferences.size() < 1)
			throw new IllegalArgumentException("Could not find any DocumentReference in PublicKey Bundle");

		if (documentReferences.size() > 1)
			logger.warn("Found {} DocumentReferences in PublicKey Bundle, using the first", documentReferences.size());

		return documentReferences.get(0);
	}

	private Binary getBinary(Bundle bundle)
	{
		List<Binary> binaries = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(r -> r instanceof Binary).map(b -> (Binary) b).toList();

		if (binaries.size() < 1)
			throw new IllegalArgumentException("Could not find any Binary in PublicKey Bundle");

		if (binaries.size() > 1)
			logger.warn("Found {} Binaries in PublicKey Bundle, using the first", binaries.size());

		return binaries.get(0);
	}

	private PublicKey getPublicKey(Binary binary, String publicKeyBundleId)
	{
		try
		{
			return KeyProvider.fromBytes(binary.getContent());
		}
		catch (Exception exception)
		{
			logger.warn("Could not read PublicKey from Binary in PublicKey Bundle with id '{}' - {}", publicKeyBundleId,
					exception.getMessage());
			throw new RuntimeException("Could not read PublicKey from Binary in PublicKey Bundle with id '"
					+ publicKeyBundleId + "' - " + exception.getMessage(), exception);
		}
	}

	private void checkHash(DocumentReference documentReference, PublicKey publicKey)
	{
		long numberOfHashes = documentReference.getContent().stream()
				.filter(DocumentReference.DocumentReferenceContentComponent::hasAttachment)
				.map(DocumentReference.DocumentReferenceContentComponent::getAttachment).filter(Attachment::hasHash)
				.map(Attachment::getHash).count();

		if (numberOfHashes < 1)
			throw new RuntimeException("Could not find any sha256-hash in DocumentReference");

		if (numberOfHashes > 1)
			logger.warn("DocumentReference contains {} sha256-hashes, using the first", numberOfHashes);

		byte[] documentReferenceHash = documentReference.getContentFirstRep().getAttachment().getHash();
		byte[] publicKeyHash = DigestUtils.sha256(publicKey.getEncoded());

		logger.debug("DocumentReference PublicKey sha256-hash '{}'", Hex.encodeHexString(documentReferenceHash));
		logger.debug("PublicKey actual sha256-hash '{}'", Hex.encodeHexString(publicKeyHash));

		if (!Arrays.equals(documentReferenceHash, publicKeyHash))
			throw new RuntimeException(
					"Sha256-hash in DocumentReference does not match computed sha256-hash of Binary");
	}

	private byte[] encrypt(PublicKey publicKey, Bundle bundle, String sendingOrganizationIdentifier,
			String receivingOrganizationIdentifier)
	{
		try
		{
			byte[] toEncrypt = FhirContext.forR4().newXmlParser().encodeResourceToString(bundle)
					.getBytes(StandardCharsets.UTF_8);

			return RsaAesGcmUtil.encrypt(publicKey, toEncrypt, sendingOrganizationIdentifier,
					receivingOrganizationIdentifier);
		}
		catch (Exception exception)
		{
			logger.warn("Could not encrypt data-set to transmit - {}", exception.getMessage());
			throw new RuntimeException("Could not encrypt data-set to transmit - " + exception.getMessage(), exception);
		}
	}
}
