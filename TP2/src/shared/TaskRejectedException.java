package shared;

public class TaskRejectedException extends Exception {
        public TaskRejectedException() {
            super("The server has rejected the task due to limited resources.");
        }
}
