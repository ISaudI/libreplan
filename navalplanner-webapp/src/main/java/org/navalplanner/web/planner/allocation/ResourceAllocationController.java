package org.navalplanner.web.planner.allocation;

import static org.navalplanner.web.I18nHelper._;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.navalplanner.business.planner.entities.CalculatedValue;
import org.navalplanner.business.planner.entities.ResourceAllocation;
import org.navalplanner.business.planner.entities.ResourcesPerDay;
import org.navalplanner.business.planner.entities.SpecificResourceAllocation;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.Resource;
import org.navalplanner.business.resources.entities.Worker;
import org.navalplanner.web.common.IMessagesForUser;
import org.navalplanner.web.common.Level;
import org.navalplanner.web.common.MessagesForUser;
import org.navalplanner.web.common.Util;
import org.navalplanner.web.common.components.WorkerSearch;
import org.navalplanner.web.planner.PlanningState;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Button;
import org.zkoss.zul.Decimalbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ListitemRenderer;
import org.zkoss.zul.SimpleConstraint;
import org.zkoss.zul.api.Window;

/**
 * Controller for {@link ResourceAllocation} view.
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Diego Pino Garcia <dpino@igalia.com>
 */
@org.springframework.stereotype.Component("resourceAllocationController")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ResourceAllocationController extends GenericForwardComposer {

    private IResourceAllocationModel resourceAllocationModel;

    private ResourceAllocationRenderer resourceAllocationRenderer = new ResourceAllocationRenderer();

    private Component messagesContainer;

    private IMessagesForUser messagesForUser;

    private Listbox resourcesList;

    private Window window;

    private ResourceAllocationFormBinder formBinder;

    private ResourceAllocationsBeingEdited allocationsBeingEdited;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        this.window = (Window) comp;
        messagesForUser = new MessagesForUser(messagesContainer);
    }

    /**
     * Shows Resource Allocation window
     * @param task
     * @param ganttTask
     * @param planningState
     */
    public void showWindow(Task task, org.zkoss.ganttz.data.Task ganttTask,
            PlanningState planningState) {
        allocationsBeingEdited = resourceAllocationModel.initAllocationsFor(
                task, ganttTask,
                planningState);
        formBinder = new ResourceAllocationFormBinder(
                getCurrentCalculatedValue(task), resourceAllocationModel);

        Util.reloadBindings(window);
        try {
            window.doModal();
        } catch (SuspendNotAllowedException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private CalculatedValue getCurrentCalculatedValue(Task task) {
        // TODO retrieve the calculated value from task
        return CalculatedValue.NUMBER_OF_HOURS;
    }

    /**
     * Shows WorkerSearch window, add picked workers as
     * {@link SpecificResourceAllocation} to {@link ResourceAllocation} list
     * @return
     */
    public void showSearchResources() {
        WorkerSearch workerSearch = new WorkerSearch();
        workerSearch.setParent(self.getParent());
        workerSearch.afterCompose();

        Window window = workerSearch.getWindow();
        try {
            window.doModal();
        } catch (SuspendNotAllowedException e1) {
            e1.printStackTrace();
            return;
        } catch (InterruptedException e1) {
            e1.printStackTrace();
            return;
        }

        addSpecificResourceAllocations(workerSearch.getWorkers());

        Util.reloadBindings(resourcesList);
    }

    /**
     * Adds a list of {@link Worker} to {@link ResourceAllocation} list
     * @param workers
     */
    private void addSpecificResourceAllocations(List<Worker> workers) {
        for (Worker worker : workers) {
            addSpecificResourceAllocation(worker);
        }
    }

    private void addSpecificResourceAllocation(Worker worker) {
        try {
            resourceAllocationModel.addSpecificResourceAllocation(worker);
        } catch (Exception e1) {
            messagesForUser.showMessage(Level.ERROR, e1.getMessage());
        }
    }

    public enum CalculationTypeRadio {

        NUMBER_OF_HOURS(CalculatedValue.NUMBER_OF_HOURS) {
            @Override
            public String getName() {
                return _("Calculate Number of Hours");
            }
        },
        END_DATE(CalculatedValue.END_DATE) {
            @Override
            public String getName() {
                return _("Calculate End Date");
            }
        },
        RESOURCES_PER_DAY(CalculatedValue.RESOURCES_PER_DAY) {
            @Override
            public String getName() {
                return _("Calculate Resources per Day");
            }
        };
        private final CalculatedValue calculatedValue;

        private CalculationTypeRadio(CalculatedValue calculatedValue) {
            this.calculatedValue = calculatedValue;

        }

        public abstract String getName();

        public CalculatedValue getCalculatedValue() {
            return calculatedValue;
        }
    }

    public List<CalculationTypeRadio> getCalculationTypes() {
        return Arrays.asList(CalculationTypeRadio.values());
    }

    public void setCalculationTypeSelected(String enumName) {
        CalculationTypeRadio calculationTypeRadio = CalculationTypeRadio
                .valueOf(enumName);
        CalculatedValue c = calculationTypeRadio.getCalculatedValue();
    }

    /**
     * Returns list of {@link Criterion} separated by comma
     * @return
     */
    public String getTaskCriterions() {
        Set<String> criterionNames = new HashSet<String>();

        Set<Criterion> criterions = resourceAllocationModel.getCriterions();
        for (Criterion criterion : criterions) {
            criterionNames.add(criterion.getName());
        }

        return StringUtils.join(criterionNames, ",");
    }

    public String getOrderHours() {
        Task task = resourceAllocationModel.getTask();
        return (task != null && task.getHoursSpecifiedAtOrder() != null) ? task.getHoursSpecifiedAtOrder()
                .toString() : "";
    }

    public List<AllocationDTO> getResourceAllocations() {
        return allocationsBeingEdited != null ? allocationsBeingEdited
                .getCurrentAllocations() : Collections.<AllocationDTO>emptyList();
    }

    public ResourceAllocationRenderer getResourceAllocationRenderer() {
        return resourceAllocationRenderer;
    }

    // Triggered when closable button is clicked
    public void onClose(Event event) {
        window.setVisible(false);
        event.stopPropagation();
    }

    public void cancel() {
        close();
        resourceAllocationModel.cancel();
    }

    private void close() {
        self.setVisible(false);
        clear();
    }

    private void clear() {
        resourcesList.getItems().clear();
    }

    public void save() {
        resourceAllocationModel.save();
        close();
    }

    /**
     * Renders a {@link SpecificResourceAllocation} item
     * @author Diego Pino Garcia <dpino@igalia.com>
     */
    private class ResourceAllocationRenderer implements ListitemRenderer {

        @Override
        public void render(Listitem item, Object data) throws Exception {
            if (data instanceof SpecificAllocationDTO) {
                renderSpecificResourceAllocation(item,
                        (SpecificAllocationDTO) data);
            } else if (data instanceof GenericAllocationDTO) {
                renderGenericResourceAllocation(item,
                        (GenericAllocationDTO) data);
            }
        }

        private void renderSpecificResourceAllocation(Listitem item,
                final SpecificAllocationDTO data) throws Exception {
            item.setValue(data);

            // Label fields are fixed, can only be viewed
            appendLabel(item, getName(data.getResource()));

            bindResourcesPerDay(appendDecimalbox(item), data);
            // On click delete button
            appendButton(item, _("Delete")).addEventListener("onClick",
                    new EventListener() {

                        @Override
                        public void onEvent(Event event) throws Exception {
                            removeSpecificResourceAllocation(data);
                        }
                    });
        }

        private String getName(Resource resource) {
            if (resource instanceof Worker) {
                Worker worker = (Worker) resource;
                return worker.getName();
            }
            return resource.getDescription();
        }

        private void removeSpecificResourceAllocation(SpecificAllocationDTO data) {
            allocationsBeingEdited.remove(data);
            Util.reloadBindings(resourcesList);
        }

        private void renderGenericResourceAllocation(Listitem item,
                final GenericAllocationDTO data) throws Exception {
            item.setValue(data);
            // Set name
            appendLabel(item, _("Generic"));
            bindResourcesPerDay(appendDecimalbox(item), data);
            // No buttons
            appendLabel(item, "");
        }

        /**
         * Appends {@link Label} to {@link Listitem}
         * @param listitem
         * @param name
         *            value for {@link Label}
         */
        private void appendLabel(Listitem listitem, String name) {
            Label label = new Label(name);

            Listcell listCell = new Listcell();
            listCell.appendChild(label);
            listitem.appendChild(listCell);
        }

        /**
         * Appends {@link Button} to {@link Listitem}
         * @param listitem
         * @param label
         *            value for {@link Button}
         * @return
         */
        private Button appendButton(Listitem listitem, String label) {
            Button button = new Button(label);

            Listcell listCell = new Listcell();
            listCell.appendChild(button);
            listitem.appendChild(listCell);

            return button;
        }

        /**
         * Append a Textbox @{link Percentage} to listItem
         * @param listItem
         */
        private Decimalbox appendDecimalbox(Listitem item) {
            Decimalbox decimalbox = new Decimalbox();

            // Insert textbox in listcell and append to listItem
            Listcell listCell = new Listcell();
            listCell.appendChild(decimalbox);
            item.appendChild(listCell);

            return decimalbox;
        }

        private void bindResourcesPerDay(final Decimalbox decimalbox,
                final AllocationDTO data) {
            decimalbox.setConstraint(new SimpleConstraint(
                    SimpleConstraint.NO_NEGATIVE));
            Util.bind(decimalbox, new Util.Getter<BigDecimal>() {

                @Override
                public BigDecimal get() {
                    return data.getResourcesPerDay().getAmount();
                }

            }, new Util.Setter<BigDecimal>() {

                @Override
                public void set(BigDecimal value) {
                    data.setResourcesPerDay(ResourcesPerDay.amount(value));
                }
            });
        }
    }
}
