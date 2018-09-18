API Gateway is handled via... a lot of mapping and regex

First, got a lambda working with the basic tutorials:

- https://www.baeldung.com/java-aws-lambda
- https://docs.aws.amazon.com/lambda/latest/dg/create-deployment-pkg-zip-java.html

Then, tried to hook it up to an HTTP request. Had to figure out how to setup an API trigger.

Next, wanted to narrow to POST, so started looking into how API Gateways work

- Defining an API
- API Resources (paths)
- API Methods (GET< POST)
- API Deployments (test, prod)

Next, wanted the raw JSON body, and the headers, so looked into request mapping templates

- [Passing Headers](https://aws.amazon.com/premiumsupport/knowledge-center/custom-headers-api-gateway-lambda/)
- [AWS Request Mapper Reference](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-mapping-template-reference.html)

POST Request Template:

```
##  See http://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-mapping-template-reference.html
##  This template will pass through all parameters including path, querystring, header, stage variables, and context through to the integration endpoint via the body/payload
#set($allParams = $input.params())
{
"body-json" : "$util.escapeJavaScript($input.body).replaceAll("\\'", "'")",
"params" : {
#foreach($type in $allParams.keySet())
    #set($params = $allParams.get($type))
"$type" : {
    #foreach($paramName in $params.keySet())
    "$paramName" : "$util.escapeJavaScript($params.get($paramName))"
        #if($foreach.hasNext),#end
    #end
}
    #if($foreach.hasNext),#end
#end
},
"stage-variables" : {
#foreach($key in $stageVariables.keySet())
"$key" : "$util.escapeJavaScript($stageVariables.get($key))"
    #if($foreach.hasNext),#end
#end
},
"context" : {
    "account-id" : "$context.identity.accountId",
    "api-id" : "$context.apiId",
    "api-key" : "$context.identity.apiKey",
    "authorizer-principal-id" : "$context.authorizer.principalId",
    "caller" : "$context.identity.caller",
    "cognito-authentication-provider" : "$context.identity.cognitoAuthenticationProvider",
    "cognito-authentication-type" : "$context.identity.cognitoAuthenticationType",
    "cognito-identity-id" : "$context.identity.cognitoIdentityId",
    "cognito-identity-pool-id" : "$context.identity.cognitoIdentityPoolId",
    "http-method" : "$context.httpMethod",
    "stage" : "$context.stage",
    "source-ip" : "$context.identity.sourceIp",
    "user" : "$context.identity.user",
    "user-agent" : "$context.identity.userAgent",
    "user-arn" : "$context.identity.userArn",
    "request-id" : "$context.requestId",
    "resource-id" : "$context.resourceId",
    "resource-path" : "$context.resourcePath"
    }
}

```


Next, reduce POST request template:

```
##  See http://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-mapping-template-reference.html
##  Pulls unparsed JSON into a property, as well as the request headers
{
    "body-raw-json" : "$util.escapeJavaScript($input.body).replaceAll("\\'", "'")",
    "headers": {
        #foreach($param in $input.params().header.keySet())
            "$param": "$util.escapeJavaScript($input.params().header.get($param))"
            #if($foreach.hasNext),#end
        #end
    }
}
```