package ji.core;

final class MockError extends Exception {

    static final MockError SINGLETON = new MockError();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    private MockError() {}
}
