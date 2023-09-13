package de.medizininformatik_initiative.process.data_sharing.message;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.rest.api.MethodOutcome;
import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.variables.Researchers;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
import dev.dsf.bpe.v1.variables.Variables;

public class SendInitializeNewProjectDataSharing extends AbstractTaskMessageSend implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(SendInitializeNewProjectDataSharing.class);

	private final FhirClientFactory fhirClientFactory;

	public SendInitializeNewProjectDataSharing(ProcessPluginApi api, FhirClientFactory fhirClientFactory)
	{
		super(api);
		this.fhirClientFactory = fhirClientFactory;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
	}

	@Override
	protected void sendTask(DelegateExecution execution, Variables variables, Target target,
			String instantiatesCanonical, String messageName, String businessKey, String profile,
			Stream<Task.ParameterComponent> additionalInputParameters)
	{
		Objects.requireNonNull(instantiatesCanonical, "instantiatesCanonical");
		if (instantiatesCanonical.isEmpty())
			throw new IllegalArgumentException("instantiatesCanonical empty");
		Objects.requireNonNull(messageName, "messageName");
		if (messageName.isEmpty())
			throw new IllegalArgumentException("messageName empty");
		Objects.requireNonNull(businessKey, "businessKey");
		if (businessKey.isEmpty())
			throw new IllegalArgumentException("profile empty");
		Objects.requireNonNull(profile, "profile");
		if (profile.isEmpty())
			throw new IllegalArgumentException("profile empty");

		Task dsfTask = variables.getStartTask();

		try
		{
			Task task = createTask(profile, instantiatesCanonical, messageName, businessKey);
			additionalInputParameters.forEach(task::addInput);
			MethodOutcome outcome = fhirClientFactory.getFhirClient().create(task);

			if (!outcome.getCreated())
			{
				String outcomeString = api.getFhirContext().newJsonParser()
						.encodeResourceToString(outcome.getOperationOutcome());
				throw new RuntimeException("Could not initialize new data-sharing project - " + outcomeString);
			}
			else
			{
				logger.info("Initialized new data-sharing project instance having id '{}' for Task with id '{}'",
						outcome.getId(), dsfTask.getId());
			}
		}
		catch (Exception exception)
		{
			logger.warn("Could not initialize new DMS project instance for Task with id '{}' - {}", dsfTask.getId(),
					exception.getMessage());
		}
	}

	@Override
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Task.ParameterComponent projectIdentifierInput = getProjectIdentifierInput(projectIdentifier);

		String contractUrl = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL);
		Task.ParameterComponent contractUrlInput = getContractUrlInput(contractUrl);

		Stream<Task.ParameterComponent> otherInputs = Stream.of(projectIdentifierInput, contractUrlInput);

		List<String> researcherIdentifiers = ((Researchers) variables
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RESEARCHER_IDENTIFIERS)).getEntries();
		Stream<Task.ParameterComponent> researcherIdentifierInputs = getResearcherIdentifierInputs(
				researcherIdentifiers);

		Targets targets = variables.getTargets();
		Stream<Task.ParameterComponent> dicIdentifierInputs = getDicIdentifierInputs(targets);

		return Stream.of(dicIdentifierInputs, otherInputs, researcherIdentifierInputs).reduce(Stream::concat)
				.orElseThrow(() -> new RuntimeException("Could not concat streams"));
	}

	private Task.ParameterComponent getProjectIdentifierInput(String projectIdentifier)
	{
		Task.ParameterComponent projectIdentifierInput = new Task.ParameterComponent();
		projectIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);
		projectIdentifierInput.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
				.setValue(projectIdentifier));

		return projectIdentifierInput;
	}

	private Task.ParameterComponent getContractUrlInput(String contractUrl)
	{
		Task.ParameterComponent contractUrlInput = new Task.ParameterComponent();
		contractUrlInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);
		contractUrlInput.setValue(new UrlType(contractUrl));

		return contractUrlInput;
	}

	private Stream<Task.ParameterComponent> getResearcherIdentifierInputs(List<String> researchers)
	{
		return researchers.stream().map(this::transformToResearcherInput);
	}

	private Task.ParameterComponent transformToResearcherInput(String researcherIdentifier)
	{
		Task.ParameterComponent researcherIdentifierInput = new Task.ParameterComponent();
		researcherIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);
		researcherIdentifierInput.setValue(new Identifier()
				.setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER).setValue(researcherIdentifier));

		return researcherIdentifierInput;
	}

	private Stream<Task.ParameterComponent> getDicIdentifierInputs(Targets targets)
	{
		return targets.getEntries().stream().map(this::transformToInput);
	}

	private Task.ParameterComponent transformToInput(Target target)
	{
		Task.ParameterComponent dicIdentifierInput = new Task.ParameterComponent();
		dicIdentifierInput.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);
		dicIdentifierInput.setValue(new Reference()
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(target.getOrganizationIdentifierValue()))
				.setType(ResourceType.Organization.name()));

		return dicIdentifierInput;
	}

	private Task createTask(String profile, String instantiatesCanonical, String messageName, String businessKey)
	{
		Task task = new Task();
		task.setMeta((new Meta()).addProfile(profile));
		task.setStatus(Task.TaskStatus.REQUESTED);
		task.setIntent(Task.TaskIntent.ORDER);
		task.setAuthoredOn(new Date());

		task.setRequester(this.getRequester());
		task.getRestriction().addRecipient(this.getRequester());

		task.setInstantiatesCanonical(instantiatesCanonical);

		Task.ParameterComponent messageNameInput = new Task.ParameterComponent(
				new CodeableConcept(CodeSystems.BpmnMessage.messageName()), new StringType(messageName));
		task.getInput().add(messageNameInput);

		Task.ParameterComponent businessKeyInput = new Task.ParameterComponent(
				new CodeableConcept(CodeSystems.BpmnMessage.businessKey()), new StringType(businessKey));
		task.getInput().add(businessKeyInput);

		return task;
	}
}
