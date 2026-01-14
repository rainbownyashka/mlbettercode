package com.example.examplemod.model;

public class ExportResult
{
    public final String text;
    public final int itemCount;

    public ExportResult(String text, int itemCount)
    {
        this.text = text == null ? "" : text;
        this.itemCount = itemCount;
    }
}
