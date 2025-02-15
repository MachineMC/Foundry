package org.machinemc.foundry.field;

public interface PredicateFlag<T> extends FieldFlag {

    Result test(T value);

    record Result(boolean result, String message) {

        public Result {
            if (result && message != null) {
                throw new IllegalArgumentException("Result cannot be successful and have a message");
            }
        }

        public static Result success() {
            return new Result(true, null);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }

    }

}
