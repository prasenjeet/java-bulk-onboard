package com.example.bulkonboard.model;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

/**
 * Maps a single row in the CSV upload template to an account record.
 * Column names in the CSV must match the 'column' attribute (case-insensitive).
 */
@Data
public class AccountRecord {

    @CsvBindByName(column = "account_id")
    private String accountId;

    @CsvBindByName(column = "account_name")
    private String accountName;

    @CsvBindByName(column = "email")
    private String email;

    @CsvBindByName(column = "phone")
    private String phone;

    @CsvBindByName(column = "account_type")
    private String accountType;

    @CsvBindByName(column = "country")
    private String country;

    @CsvBindByName(column = "currency")
    private String currency;

    @CsvBindByName(column = "credit_limit")
    private String creditLimit;

    @CsvBindByName(column = "status")
    private String status;
}
