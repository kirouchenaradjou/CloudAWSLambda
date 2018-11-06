import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;
import java.util.logging.Logger;

public class LogEvent implements RequestHandler<SNSEvent, Object> {

    private DynamoDB myDynamoDB;
    private Regions REGION = Regions.US_EAST_1;
    protected static final String DYNAMODB_ENDPOINT = "";
    protected static String token;
    protected static String app_username;
    protected static String SES_FROM_ADDRESS;
    protected static final String EMAIL_SUBJECT = "Reset Pass";
    protected static String HTMLBODY;

    private static String TEXTBODY;

    private void connectToDynamoDb(Context context) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        context.getLogger().log( "create DynamoDB client via Builder." );
        context.getLogger().log( "DynamoDB client: " + client.toString() );
        this.myDynamoDB = new DynamoDB(client);
    }

    public Object handleRequest(SNSEvent request, Context context) {
        LambdaLogger logger = context.getLogger();

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        logger.log( "Timestamp: " + timeStamp );
        logger.log( "Request : " + (request == null) );
        logger.log( "Records" + (request.getRecords().size()) );
        app_username = request.getRecords().get(0).getSNS().getMessage();
        logger.log( "Email Address which requests reset password: " + app_username );
        token = UUID.randomUUID().toString();
        logger.log( "token: " + token );
        this.connectToDynamoDb(context);
        logger.log("DynamoDB client has been built.");

        String DBTableName = System.getenv("DynamoDBTableName");
        logger.log( "DynamoDB table name: " + DBTableName );
        SES_FROM_ADDRESS = System.getenv( "FromEmailAddress" );

        Table tableInstance = myDynamoDB.getTable( DBTableName );
        if( tableInstance!=null )
            logger.log( "Get the table from DynamoDB: " + DBTableName );
        else
            return null;

        if( ( tableInstance.getItem( "id", app_username ) ) == null ) {

            logger.log("User's Reset Request does not exist in the dynamo db table.Creates a new token");

            Number terminatedTime = System.currentTimeMillis() / 1000L + 1200; //20 mins
            logger.log( "token invalid time: " + terminatedTime );
            this.myDynamoDB.getTable(DBTableName)
                    .putItem(
                            new PutItemSpec().withItem( new Item()
                                    .withString( "id", app_username)
                                    .withString( "token", token )
                                    .withNumber( "ttl", terminatedTime ) ) );

            TEXTBODY = "https://reset?email=" + app_username + "&token=" + token;
            logger.log( "This is text body: " + TEXTBODY );
            HTMLBODY = "<h3>You have successfully requested an Password Reset using Amazon SES!</h3>"
                    + "<p>Please reset the password using the below link in 20 minutes.<br/> " +
                    "Link: https:///reset?email=" + app_username + "&token=" + token+"</p>";
            logger.log( "This is HTML body: " + HTMLBODY );

            logger.log( "=================step 2==============" );
            try {
                AmazonSimpleEmailService sesClient = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion( REGION ).build();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(
                                new Destination().withToAddresses(app_username) )
                        .withMessage( new Message()
                                .withBody( new Body()
                                        .withHtml( new Content()
                                                .withCharset( "UTF-8" ).withData( HTMLBODY ) )
                                        .withText( new Content()
                                                .withCharset( "UTF-8" ).withData( TEXTBODY ) ) )
                                .withSubject( new Content()
                                        .withCharset( "UTF-8" ).withData(EMAIL_SUBJECT) ) )
                        .withSource(SES_FROM_ADDRESS);
                sesClient.sendEmail( emailRequest );
                System.out.println( "Email successfully sent!" );
                logger.log("Email successfully sent!");
            } catch (Exception ex) {
                System.out.println( "The email was not sent. Error message: "
                        + ex.getMessage() );
                logger.log("The email was not sent");
            }

        }
        else {
            logger.log("User's Reset Request exists in the dynamo db table");
        }
        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        logger.log("Done Timestamp: " + timeStamp);
        return null;
    }
}