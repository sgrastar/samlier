package org.samlier.core.evaluation;

public enum Rfc2119Level {
    MUST(LevelClass.MUST_CLASS),
    MUST_NOT(LevelClass.MUST_CLASS),
    REQUIRED(LevelClass.MUST_CLASS),
    SHOULD(LevelClass.SHOULD_CLASS),
    SHOULD_NOT(LevelClass.SHOULD_CLASS),
    RECOMMENDED(LevelClass.SHOULD_CLASS),
    NOT_RECOMMENDED(LevelClass.SHOULD_CLASS),
    MAY(LevelClass.MAY_CLASS),
    OPTIONAL(LevelClass.MAY_CLASS);

    private final LevelClass levelClass;

    Rfc2119Level(LevelClass levelClass) {
        this.levelClass = levelClass;
    }

    public LevelClass levelClass() {
        return levelClass;
    }

    public enum LevelClass { MUST_CLASS, SHOULD_CLASS, MAY_CLASS }
}
