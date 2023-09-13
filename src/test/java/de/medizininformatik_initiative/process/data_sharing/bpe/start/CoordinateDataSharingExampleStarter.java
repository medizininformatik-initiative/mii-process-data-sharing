package de.medizininformatik_initiative.process.data_sharing.bpe.start;

import java.util.Date;
import java.util.UUID;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskIntent;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.UrlType;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.process.data_sharing.DataSharingProcessPluginDefinition;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.start.ExampleStarter;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;

public class CoordinateDataSharingExampleStarter
{
	private static final String HRP_URL = "https://hrp/fhir";
	private static final String HRP_IDENTIFIER = "Test_HRP";
	private static final String DMS_IDENTIFIER = "Test_DMS";
	private static final String DIC1_IDENTIFIER = "Test_DIC1";
	private static final String DIC2_IDENTIFIER = "Test_DIC2";

	public static void main(String[] args) throws Exception
	{
		ExampleStarter.forServer(args, HRP_URL).startWith(task());
	}

	private static Task task()
	{
		var def = new DataSharingProcessPluginDefinition();

		Task task = new Task();
		task.setIdElement(new IdType("urn:uuid:" + UUID.randomUUID().toString()));

		task.getMeta()
				.addProfile(ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(HRP_IDENTIFIER));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(HRP_IDENTIFIER));

		task.addInput().setValue(new StringType(ConstantsDataSharing.PROFILE_TASK_COORDINATE_DATA_SHARING_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());

		task.addInput()
				.setValue(new Identifier().setSystem(ConstantsBase.NAMINGSYSTEM_MII_PROJECT_IDENTIFIER)
						.setValue("Test_PROJECT_Bundle"))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER);

		task.addInput().setValue(new UrlType("http://forschen-fuer-gesundheit.de/contract/test_project")).getType()
				.addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL);

		task.addInput().setValue(new StringType("PT10M")).getType().addCoding()
				.setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_EXTRACTION_PERIOD);

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
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue(DIC1_IDENTIFIER))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);
		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue(DIC2_IDENTIFIER))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER);

		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue(DMS_IDENTIFIER))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER);

		return task;
	}
}
