package de.medizininformatik_initiative.process.data_sharing.questionnaire;

import java.util.Optional;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.DefaultUserTaskListener;
import dev.dsf.bpe.v2.activity.values.CreateQuestionnaireResponseValues;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

;

public class ReleaseConsolidateDataSetsListener extends DefaultUserTaskListener
{
	public ReleaseConsolidateDataSetsListener()
	{
	}

	@Override
	protected void beforeQuestionnaireResponseCreate(ProcessPluginApi api, Variables variables,
			CreateQuestionnaireResponseValues createQuestionnaireResponseValues, QuestionnaireResponse beforeCreate)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Targets targets = variables.getTargets();

		Optional<QuestionnaireResponse.QuestionnaireResponseItemComponent> displayItem = beforeCreate.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_DISPLAY.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasText).findFirst();

		if (displayItem.isPresent())
		{
			String text = replaceText(displayItem.get().getText(), projectIdentifier, dmsIdentifier, targets);
			displayItem.get().setText(text);
		}

		beforeCreate.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_EXTENDED_EXTRACTION_PERIOD.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasAnswer)
				.flatMap(i -> i.getAnswer().stream()).forEach(a -> replaceExtendedExtractionPeriodAnswerPlaceholder(a,
						ConstantsDataSharing.EXTENDED_DATA_EXTRACTION_PERIOD_DEFAULT_VALUE));
	}

	@Override
	protected void afterQuestionnaireResponseCreate(ProcessPluginApi api, Variables variables,
			CreateQuestionnaireResponseValues createQuestionnaireResponseValues, QuestionnaireResponse afterCreate)
	{
		Task task = variables.getStartTask();
		String absoluteId = getDsfFhirServerAbsoluteId(api, afterCreate.getIdElement());
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		String subject = "New user-task in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING
				+ "'";
		String message = "A new user-task 'release-consolidate-data-sets' for data-sharing project '"
				+ projectIdentifier + " and DMS '" + dmsIdentifier + "' in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "' for Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task)
				+ "' is waiting for it's completion. It can be accessed using the following link:\n" + "- "
				+ absoluteId;

		api.getMailService().send(subject, message);
	}

	private String replaceText(String toReplace, String projectIdentifier, String dmsIdentifier, Targets targets)
	{
		String dicIdentifiers = "";

		if (targets.isEmpty())
			dicIdentifiers = dicIdentifiers + "There are no missing data-sets";
		else
			dicIdentifiers = dicIdentifiers + "[ " + targets.getEntries().stream()
					.map(Target::getOrganizationIdentifierValue).collect(Collectors.joining(" ; ")) + " ]";

		return toReplace
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_PROJECT_IDENTIFIER,
						"\"" + projectIdentifier + "\"")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DMS_IDENTIFIER, "\"" + dmsIdentifier + "\"")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DIC_IDENTIFIERS, dicIdentifiers);
	}

	private void replaceExtendedExtractionPeriodAnswerPlaceholder(
			QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer, String extendedExtractionPeriod)
	{
		if (answer.getValue() instanceof StringType)
			answer.setValue(new StringType(extendedExtractionPeriod));
	}

	private String getDsfFhirServerAbsoluteId(ProcessPluginApi api, IdType idType)
	{
		return new IdType(api.getDsfClientProvider().getLocal().getBaseUrl(), idType.getResourceType(),
				idType.getIdPart(), idType.getVersionIdPart()).getValue();
	}
}
