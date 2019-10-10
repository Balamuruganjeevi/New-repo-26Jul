package org.openchs.web.request;

import org.openchs.domain.Program;

public class ProgramRequest {

    private String name;
    private String uuid;
    private String colour;
    private boolean voided;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public static ProgramRequest fromProgram(Program program) {
        ProgramRequest programRequest = new ProgramRequest();
        programRequest.setUuid(program.getUuid());
        programRequest.setName(program.getName());
        programRequest.setColour(program.getColour());
        programRequest.setVoided(program.isVoided());
        return programRequest;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }
}
