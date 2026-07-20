package com.x.business.controller;

import com.x.business.dto.BusinessResponse;
import com.x.business.dto.CreateBusinessRequest;
import com.x.business.service.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessResponse create(@Valid @RequestBody CreateBusinessRequest request) {
        return businessService.create(request);
    }

    @GetMapping("/{id}")
    public BusinessResponse getById(@PathVariable @Positive Long id) {
        return businessService.getById(id);
    }

    @GetMapping
    public List<BusinessResponse> getByOwner(@RequestParam @Positive Long ownerUserId) {
        return businessService.getByOwner(ownerUserId);
    }
}
