package de.medizininformatik_initiative.process.data_sharing.questionnaire;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.DefaultUserTaskListener;
import dev.dsf.bpe.v1.constants.BpmnExecutionVariables;
import dev.dsf.bpe.v1.service.FhirWebserviceClientProvider;
import dev.dsf.bpe.v1.service.MailService;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;

public class ReleaseConsolidateDataSetsListener extends DefaultUserTaskListener implements InitializingBean
{
	private final FhirWebserviceClientProvider fhirDsfClientProvider;
	private final MailService mailService;

	public ReleaseConsolidateDataSetsListener(ProcessPluginApi api)
	{
		super(api);
		this.fhirDsfClientProvider = api.getFhirWebserviceClientProvider();
		this.mailService = api.getMailService();
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(fhirDsfClientProvider, "fhirDsfClientProvider");
		Objects.requireNonNull(mailService, "mailService");
	}

	@Override
	protected void beforeQuestionnaireResponseCreate(DelegateTask userTask, QuestionnaireResponse questionnaireResponse)
	{
		String projectIdentifier = (String) userTask.getExecution()
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = (String) userTask.getExecution()
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Targets targets = (Targets) userTask.getExecution().getVariable(BpmnExecutionVariables.TARGETS);

		Optional<QuestionnaireResponse.QuestionnaireResponseItemComponent> displayItem = questionnaireResponse.getItem()
				.stream().filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_DISPLAY.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasText).findFirst();

		if (displayItem.isPresent())
		{
			String text = replaceText(displayItem.get().getText(), projectIdentifier, dmsIdentifier, targets);
			displayItem.get().setText(text);
		}

		questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_EXTENDED_EXTRACTION_PERIOD.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.flatMap(i -> i.getAnswer().stream()).forEach(a -> replaceExtendedExtractionPeriodAnswerPlaceholder(a,
						ConstantsDataSharing.EXTENDED_DATA_EXTRACTION_PERIOD_DEFAULT_VALUE));
	}

	@Override
	protected void afterQuestionnaireResponseCreate(DelegateTask userTask, QuestionnaireResponse questionnaireResponse)
	{
		IdType id = questionnaireResponse.getIdElement();
		IdType absoluteId = new IdType(fhirDsfClientProvider.getLocalWebserviceClient().getBaseUrl(),
				id.getResourceType(), id.getIdPart(), null);

		String projectIdentifier = (String) userTask.getExecution()
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = (String) userTask.getExecution()
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		String subject = "New user-task in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING
				+ "'";
		String message = "A new user-task 'release-consolidate-data-sets' for data-sharing project '"
				+ projectIdentifier + " and DMS '" + dmsIdentifier + "' in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING
				+ "' is waiting for it's completion. It can be accessed using the following link:\n" + "- "
				+ absoluteId.getValue();

		mailService.send(subject, message);
	}

	private String replaceText(String toReplace, String projectIdentifier, String dmsIdentifier, Targets targets)
	{
		String dicIdentifiers = "</br> - ";

		if (targets.isEmpty())
			dicIdentifiers = dicIdentifiers + "There are no missing data-sets";
		else
			dicIdentifiers = dicIdentifiers + targets.getEntries().stream().map(Target::getOrganizationIdentifierValue)
					.collect(Collectors.joining("</br> - "));

		return toReplace
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_PROJECT_IDENTIFIER,
						"<b>" + projectIdentifier + "</b>")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DMS_IDENTIFIER, "<b>" + dmsIdentifier + "</b>")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DIC_IDENTIFIERS, dicIdentifiers)
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_NEW_LINE, "</br>");
	}

	private void replaceExtendedExtractionPeriodAnswerPlaceholder(
			QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer, String extendedExtractionPeriod)
	{
		if (answer.getValue() instanceof StringType)
			answer.setValue(new StringType(extendedExtractionPeriod));
	}
}
