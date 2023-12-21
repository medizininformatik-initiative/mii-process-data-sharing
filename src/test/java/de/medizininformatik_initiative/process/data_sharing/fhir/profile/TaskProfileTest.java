package de.medizininformatik_initiative.process.data_sharing.fhir.profile;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskIntent;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.UrlType;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.DataSharingProcessPluginDefinition;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.fhir.validation.ResourceValidator;
import dev.dsf.fhir.validation.ResourceValidatorImpl;
import dev.dsf.fhir.validation.ValidationSupportRule;

public class TaskProfileTest
{
	private static final Logger logger = LoggerFactory.getLogger(TaskProfileTest.class);
	private static final DataSharingProcessPluginDefinition def = new DataSharingProcessPluginDefinition();

	@ClassRule
	public static final ValidationSupportRule validationRule = new ValidationSupportRule(def.getResourceVersion(),
			def.getResourceReleaseDate(),
			Arrays.asList("dsf-task-base-1.0.0.xml", "extension-dic-identifier.xml",
					"extension-data-set-status-error.xml", "task-consolidate-data-sets.xml",
					"task-coordinate-data-sharing.xml", "task-execute-data-sharing.xml", "task-merge-data-sharing.xml",
					"task-send-data-set.xml", "task-status-data-set.xml", "task-merged-data-set.xml",
					"task-received-data-set.xml"),
			Arrays.asList("dsf-read-access-tag-1.0.0.xml", "dsf-bpmn-message-1.0.0.xml", "data-sharing.xml",
					"mii-cryptography.xml", "mii-data-set-status.xml"),
			Arrays.asList("dsf-read-access-tag-1.0.0.xml", "dsf-bpmn-message-1.0.0.xml", "data-sharing.xml",
					"mii-cryptography.xml", "mii-data-set-status-receive.xml", "mii-data-set-status-send.xml"));

	private final ResourceValidator resourceValidator = new ResourceValidatorImpl(validationRule.getFhirContext(),
			validationRule.getValidationSupport());

	@Test
	public void testValidTaskCoordinateDataSharing()
	{
		Task task = createValidTaskCoordinateDataSharing();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskCoordinateDataSharing()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
						.setValue("Test_PROJECT"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);

		task.addInput().setValue(new UrlType("http://forschen-fuer-gesundheit.de/contract/test_project")).getType()
				.addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER)
						.setValue("Test_Researcher1"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);
		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER)
						.setValue("Test_Researcher2"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);

		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);
		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC2"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);

		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER);

		task.addOutput().setValue(new UrlType("http://example.foo")).getType().addCoding()
				.setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);

		task.addOutput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_MISSING);

		return task;
	}

	@Test
	public void testValidTaskExecuteDataSharing()
	{
		Task task = createValidTaskExecuteDataSharing();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testValidTaskExecuteDataSharingWithReportStatusOutput()
	{
		Task task = createValidTaskExecuteDataSharing();
		task.addOutput(new DataSetStatusGenerator().createDataSetStatusOutput(
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_OK, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testValidTaskExecuteDataSharingWithReportStatusErrorOutput()
	{
		Task task = createValidTaskExecuteDataSharing();
		task.addOutput(new DataSetStatusGenerator().createDataSetStatusOutput(
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_OK, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "some error message"));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskExecuteDataSharing()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_EXECUTE_DATA_SHARING);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_EXECUTE_DATA_SHARING_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_EXECUTE_DATA_SHARING_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.correlationKey());

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
						.setValue("Test_PROJECT"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);

		task.addInput().setValue(new UrlType("http://forschen-fuer-gesundheit.de/contract/test_project")).getType()
				.addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);

		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER);

		return task;
	}

	@Test
	public void testValidTaskMergeDataSharing()
	{
		Task task = createValidTaskMergeDataSharing();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskMergeDataSharing()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_MERGE_DATA_SHARING);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_MERGE_DATA_SHARING_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_MERGE_DATA_SHARING_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
						.setValue("Test_PROJECT"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);

		task.addInput().setValue(new UrlType("http://forschen-fuer-gesundheit.de/contract/test_project")).getType()
				.addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER)
						.setValue("Test_Researcher1"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);
		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsDataSharing.NAMINGSYSTEM_RESEARCHER_IDENTIFIER)
						.setValue("Test_Researcher2"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_RESEARCHER_IDENTIFIER);

		Task.ParameterComponent dic1 = task.addInput().setValue(new StringType(UUID.randomUUID().toString()));
		dic1.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY);
		dic1.addExtension().setUrl(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER)
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"))
						.setType(ResourceType.Organization.name()));

		Task.ParameterComponent dic2 = task.addInput().setValue(new StringType(UUID.randomUUID().toString()));
		dic2.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_CORRELATION_KEY);
		dic2.addExtension().setUrl(ConstantsDataSharing.EXTENSION_URL_DIC_IDENTIFIER)
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC2"))
						.setType(ResourceType.Organization.name()));

		task.addOutput()
				.setValue(new Reference("http://example.foo/fhir/DocumentReference/1")
						.setType(ResourceType.DocumentReference.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DOCUMENT_REFERENCE_REFERENCE);

		task.addOutput().setValue(new UrlType("http://example.foo")).getType().addCoding()
				.setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);

		task.addOutput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_MISSING);

		return task;
	}

	@Test
	public void testValidTaskSendDataSet()
	{
		Task task = createValidTaskSendDataSet();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskSendDataSet()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_SEND_DATA_SET);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_MERGE_DATA_SHARING_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_SEND_DATA_SET_MESSAGE_NAME)).getType()
				.addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.correlationKey());

		task.addInput()
				.setValue(new Reference().setReference("https://dic1/fhir/Binary/" + UUID.randomUUID().toString())
						.setType(ResourceType.Binary.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_REFERENCE);

		return task;
	}

	@Test
	public void testValidTaskDataSetStatusWithResponseInput()
	{
		Task task = createValidTaskDataSetStatus();
		task.addInput(new DataSetStatusGenerator().createDataSetStatusInput(
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_OK, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testValidTaskDataSetStatusWithResponseInputError()
	{
		Task task = createValidTaskDataSetStatus();
		task.addInput(new DataSetStatusGenerator().createDataSetStatusInput(
				ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIPT_ERROR,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
				ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, "some error message"));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskDataSetStatus()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_STATUS_DATA_SET + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_STATUS_DATA_SET_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));

		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_STATUS_DATA_SET_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		return task;
	}

	@Test
	public void testValidTaskSendReceivedDataSet()
	{
		Task task = createValidTaskSendReceivedDataSet();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskSendReceivedDataSet()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_RECEIVED_DATA_SET);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_RECEIVED_DATA_SET_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_RECEIVED_DATA_SET_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC1"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);

		return task;
	}

	@Test
	public void testValidTaskConsolidateDataSets()
	{
		Task task = createValidTaskConsolidateDataSets();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskConsolidateDataSets()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_CONSOLIDATE_DATA_SETS);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_CONSOLIDATE_DATA_SETS_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_CONSOLIDATE_DATA_SETS_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		return task;
	}

	@Test
	public void testValidTaskSendMergedDataSet()
	{
		Task task = createValidTaskSendMergedDataSet();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskSendMergedDataSet()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsDataSharing.PROFILE_TASK_MERGED_DATA_SET);
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_MERGED_DATA_SET_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DMS"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"));
		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_MERGED_DATA_SET_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());
		task.addInput().setValue(new UrlType("http://test.foo")).getType().addCoding()
				.setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_URL);

		return task;
	}
}
