package de.medizininformatik_initiative.process.data_sharing.questionnaire;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.DefaultUserTaskListener;
import dev.dsf.bpe.v2.activity.values.CreateQuestionnaireResponseValues;
import dev.dsf.bpe.v2.variables.Variables;

public class ReleaseDataSetListener extends DefaultUserTaskListener
{
	private final String fhirStoreId;
	private final boolean dicEmailEnabled;

	public ReleaseDataSetListener(String fhirStoreId, boolean dicEmailEnabled)
	{
		this.fhirStoreId = fhirStoreId;
		this.dicEmailEnabled = dicEmailEnabled;
	}

	@Override
	protected void beforeQuestionnaireResponseCreate(ProcessPluginApi api, Variables variables,
			CreateQuestionnaireResponseValues createQuestionnaireResponseValues, QuestionnaireResponse beforeCreate)
	{
		String fhirStoreBaseUrl = getDsfFhirServerAbsoluteIdById(api, fhirStoreId);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

		beforeCreate.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_DISPLAY.equals(i.getLinkId())
						|| ConstantsDataSharing.QUESTIONNAIRES_ITEM_RELEASE.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasText)
				.forEach(i -> replace(i, projectIdentifier, dmsIdentifier, fhirStoreBaseUrl));
	}

	@Override
	protected void afterQuestionnaireResponseCreate(ProcessPluginApi api, Variables variables,
			CreateQuestionnaireResponseValues createQuestionnaireResponseValues, QuestionnaireResponse afterCreate)
	{
		variables.setFhirResource(
				ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_RELEASE_DATA_SET_INITIAL_QUESTIONNAIRE_RESPONSE,
				afterCreate);

		if (dicEmailEnabled)
		{
			Task task = variables.getStartTask();
			String absoluteId = getDsfFhirServerAbsoluteIdLocal(api, afterCreate.getIdElement());
			String projectIdentifier = variables
					.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
			String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);

			String subject = "New user-task in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING
					+ "'";
			String message = "A new user-task 'release-data-set' for data-sharing project '" + projectIdentifier
					+ " and DMS '" + dmsIdentifier + "' in process '"
					+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' for Task '"
					+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task)
					+ "' is waiting for it's completion. It can be accessed using the following link:\n" + "- "
					+ absoluteId;

			api.getMailService().send(subject, message);
		}
	}

	private void replace(QuestionnaireResponse.QuestionnaireResponseItemComponent item, String projectIdentifier,
			String dmsIdentifier, String fhirStoreBaseUrl)
	{
		String finalText = replaceText(item.getText(), projectIdentifier, dmsIdentifier, fhirStoreBaseUrl);
		item.setText(finalText);

		item.getAnswer().stream().filter(a -> a.getValue() instanceof StringType)
				.forEach(a -> replaceAnswerStringTypePlaceholder(a, projectIdentifier));
	}

	private void replaceAnswerStringTypePlaceholder(
			QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer, String projectIdentifier)
	{
		if (answer.getValue() instanceof StringType)
			answer.setValue(new StringType(projectIdentifier));
	}

	private String replaceText(String toReplace, String projectIdentifier, String dmsIdentifier,
			String fhirStoreBaseUrl)
	{
		return toReplace
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_PROJECT_IDENTIFIER,
						"\"" + projectIdentifier + "\"")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DMS_IDENTIFIER, "\"" + dmsIdentifier + "\"")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_FHIR_STORE_BASE_URL,
						"\"" + fhirStoreBaseUrl + "\"");
	}

	private String getDsfFhirServerAbsoluteIdLocal(ProcessPluginApi api, IdType idType)
	{
		return new IdType(api.getDsfClientProvider().getLocal().getBaseUrl(), idType.getResourceType(),
				idType.getIdPart(), idType.getVersionIdPart()).getValue();
	}

	private String getDsfFhirServerAbsoluteIdById(ProcessPluginApi api, String fhirStoreId)
	{
		return api.getDsfClientProvider().getById(fhirStoreId)
				.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"))
				.getBaseUrl();
	}
}
