package de.medizininformatik_initiative.process.data_sharing.questionnaire;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.DefaultUserTaskListener;
import dev.dsf.bpe.v1.service.FhirWebserviceClientProvider;
import dev.dsf.bpe.v1.service.MailService;

public class ReleaseDataSetListener extends DefaultUserTaskListener implements InitializingBean
{
	private final FhirClientFactory fhirStoreClientFactory;
	private final FhirWebserviceClientProvider fhirDsfClientProvider;
	private final MailService mailService;

	public ReleaseDataSetListener(ProcessPluginApi api, FhirClientFactory fhirClientFactory)
	{
		super(api);
		this.mailService = api.getMailService();
		this.fhirDsfClientProvider = api.getFhirWebserviceClientProvider();
		this.fhirStoreClientFactory = fhirClientFactory;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(fhirStoreClientFactory, "fhirClientFactory");
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
		String fhirStoreBaseUrl = fhirStoreClientFactory.getFhirClient().getFhirBaseUrl();

		questionnaireResponse.getItem().stream()
				.filter(i -> ConstantsDataSharing.QUESTIONNAIRES_ITEM_DISPLAY.equals(i.getLinkId())
						|| ConstantsDataSharing.QUESTIONNAIRES_ITEM_RELEASE.equals(i.getLinkId()))
				.filter(QuestionnaireResponse.QuestionnaireResponseItemComponent::hasText)
				.forEach(i -> replace(i, projectIdentifier, dmsIdentifier, fhirStoreBaseUrl));
	}

	@Override
	protected void afterQuestionnaireResponseCreate(DelegateTask userTask, QuestionnaireResponse questionnaireResponse)
	{
		IdType id = questionnaireResponse.getIdElement();
		IdType absoluteId = new IdType(fhirDsfClientProvider.getLocalWebserviceClient().getBaseUrl(),
				id.getResourceType(), id.getIdPart(), null);

		String projectIdentifier = (String) userTask.getExecution()
				.getVariable(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		String subject = "New user-task in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING
				+ "'";
		String message = "A new user-task 'release-data-set' for data-sharing project '" + projectIdentifier
				+ "' in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING
				+ "' is waiting for it's completion. It can be accessed using the following link:\n" + "- "
				+ absoluteId.getValue();

		mailService.send(subject, message);
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
						"<b>" + projectIdentifier + "</b>")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_DMS_IDENTIFIER, "<b>" + dmsIdentifier + "</b>")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_FHIR_STORE_BASE_URL,
						"<b>" + fhirStoreBaseUrl + "</b>")
				.replace(ConstantsDataSharing.QUESTIONNAIRES_PLACEHOLDER_NEW_LINE, "</br>");
	}
}
