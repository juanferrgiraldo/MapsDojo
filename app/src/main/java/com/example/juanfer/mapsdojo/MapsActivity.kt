package com.example.juanfer.mapsdojo

import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient  //Determine device location.
    private lateinit var lastLocation: Location
    internal lateinit var MarkerPoints: ArrayList<LatLng>

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onMarkerClick(p0: Marker?) = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        MarkerPoints = ArrayList<LatLng>()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setOnMarkerClickListener(this)
        setUpMap()
        mMap!!.setOnMapClickListener { point ->
            //Already two locations
            if(MarkerPoints.size > 1) {
                MarkerPoints.clear()
                mMap!!.clear()
            }

            //Adding new item to the ArryList
            MarkerPoints.add(point)

            //Creating MarkerOptions
            val options = MarkerOptions()

            //Setting the position to the marker
            options.position(point)

            //Green for the start and red for the end
            if(MarkerPoints.size == 1) {
                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            } else {
                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }

            //Add new marker to the Google Map Android API V2
            mMap!!.addMarker(options)

            //Checks, wheter start and end locations are captured
            if(MarkerPoints.size >= 2) {
                val origin = MarkerPoints[0]
                val dest = MarkerPoints[1]

                //Getting URL to the Google Directions API
                val url = getUrl(origin, dest)
                Log.d("onMapClick", url.toString())
                val FetchUrl = FetchUrl()

                //Start downloading JSON data from Google Directions API
                FetchUrl.execute(url)
                //Move camera
                mMap!!.moveCamera(CameraUpdateFactory.newLatLng(origin))
                mMap!!.animateCamera(CameraUpdateFactory.zoomTo(11f))
            }
        }
    }

    private fun setUpMap() {
        if(ActivityCompat.checkSelfPermission(this, //Checks if the app has granted the ACCESS_FINE_LOCATION
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,     //If not request from the user.
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        mMap.isMyLocationEnabled = true  //draw and center the map on users location

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->  //gives the most recent location available
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                placeMarkerOnMap(currentLatLng)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            }
        }
    }

    private fun placeMarkerOnMap(location: LatLng) {
        val markerOptions = MarkerOptions().position(location)  //Sets the current location as position to the marker
        mMap.addMarker(markerOptions)
    }

    private fun getUrl(origin: LatLng, dest: LatLng): String {
        //Origin of route
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude

        //Destination of route
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude

        //Sensor enabled
        val sensor = "sensor=false"

        //Building the parameters to the web service
        val parameters = "$str_origin$str_dest$sensor"  //$ references a variable in a string template

        //Output format
        val output = "json"

        //Building the url to the web service
        val url = "https://maps.googleapis.com/maps/api/directions/$output?$parameters"

        return url
    }

    //Fetches data from url passed
    private inner class FetchUrl : AsyncTask<String, Void, String>() { //to access outer class members
        override fun doInBackground(vararg url: String): String {
            //For storing data from web service
            var data = ""

            try {
                //fetching the data from web service
                data = downloadUrl(url[0])
                Log.d("Background Task data", data.toString())
            } catch (e: Exception) {
                Log.d("Background Task", e.toString())
            }
            return data
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            val parserTask = ParserTask()

            //Invokes the thread for parsing the JSON data
            parserTask.execute(result)
        }
    }

    private inner class ParserTask : AsyncTask<String, Int, List<List<HashMap<String, String>>>>() {

        override fun doInBackground(vararg jsonData: String?): List<List<HashMap<String, String>>> {
           val jObject: JSONObject

            try{
                jObject = JSONObject(jsonData[0])
                Log.d("ParserTask", jsonData[0])
                val parser = DataParser()
                Log.d("ParserTask", parser.toString())

                // Starts parsing data
                var routes: List<List<HashMap<String, String>>>  = parser.parse(jObject)
                Log.d("ParserTask", "Executing routes")
                Log.d("ParserTask", routes.toString())
                return routes
            } catch (e: Exception) {
                Log.d("ParserTask", e.toString())
                e.printStackTrace();
            }
            val r:List<List<HashMap<String, String>>> = ArrayList<ArrayList<HashMap<String, String>>>()
            return r
        }
        // Executes in UI thread, after the parsing process
        override fun onPostExecute(result: List<List<HashMap<String, String>>>) {
            var points: ArrayList<LatLng>
            var lineOptions: PolylineOptions? = null

            // Traversing through all the routes
            for (i in result.indices) {
                points = ArrayList<LatLng>()
                lineOptions = PolylineOptions()

                // Fetching i-th route
                val path = result[i]

                // Fetching all the points in i-th route
                for (j in path.indices) {
                    val point = path[j]

                    val lat = java.lang.Double.parseDouble(point["lat"])
                    val lng = java.lang.Double.parseDouble(point["lng"])
                    val position = LatLng(lat, lng)

                    points.add(position)
                }

                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points)
                lineOptions.width(10f)
                lineOptions.color(Color.RED)

                Log.d("onPostExecute", "onPostExecute lineoptions decoded")

            }

            // Drawing polyline in the Google Map for the i-th route
            if (lineOptions != null) {
                mMap!!.addPolyline(lineOptions)
            } else {
                Log.d("onPostExecute", "without Polylines drawn")
            }
        }
    }

    @Throws(IOException::class)
    private fun downloadUrl(strUrl: String): String {
        var data = ""
        var iStream: InputStream? = null
        var urlConnetion: HttpURLConnection? = null

        try {
            val url = URL(strUrl)

            //Creating an http connection to communicate with url
            urlConnetion = url.openConnection() as HttpURLConnection

            //Connecting to url
            urlConnetion.connect()

            //Reading data from url
            iStream = urlConnetion.inputStream

            val br = BufferedReader(InputStreamReader(iStream!!))
            val sb = StringBuffer()
            var line = ""

            while (line != null) {
                line = br.readLine()
                sb.append(line)
            }

            data = sb.toString()
            Log.d("downloadUrl", data.toString())
            br.close()
        } catch (e: Exception) {
            Log.d("Exception", e.toString())
        } finally {
            iStream!!.close()
            urlConnetion!!.disconnect()
        }
        return data
    }
}
